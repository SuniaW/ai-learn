/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wx.ai.learn.advisor;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * 自我优化评估顾问（Self-Refine Evaluation Advisor）
 * 该顾问通过一个独立的 LLM（作为“裁判”）对主模型的响应进行评分。
 * 如果评分低于设定阈值，则将具体的改进建议反馈给主模型，并触发重试。
 * 最多重试 maxRepeatAttempts 次，避免无限循环。
 * 适用于需要高回答质量的场景，例如问答系统、内容生成等。
 * 注意：仅支持非流式调用（call），不支持流式（stream）。
 *
 * @author Christian Tzolov
 */
public final class SelfRefineEvaluationAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SelfRefineEvaluationAdvisor.class);

    /**
     * 默认的评估提示模板。
     * 要求评估模型对“用户问题-助手回答”对进行 1~4 分打分，并提供解释和具体反馈。
     * 输出格式需严格包含 "Total rating:"、"Evaluation:" 和 "Feedback:" 字段。
     */
    private static final PromptTemplate DEFAULT_EVALUATION_PROMPT_TEMPLATE = new PromptTemplate(
            """
                        You will be given a user_question and assistant_answer couple.
                        Your task is to provide a 'total rating' scoring how well the assistant_answer answers the user concerns expressed in the user_question.
                        Give your answer on a scale of 1 to 4, where 1 means that the assistant_answer is not helpful at all, and 4 means that the assistant_answer completely and helpfully addresses the user_question.
                    
                        Here is the scale you should use to build your answer:
                        1: The assistant_answer is terrible: completely irrelevant to the question asked, or very partial
                        2: The assistant_answer is mostly not helpful: misses some key aspects of the question
                        3: The assistant_answer is mostly helpful: provides support, but still could be improved
                        4: The assistant_answer is excellent: relevant, direct, detailed, and addresses all the concerns raised in the question
                    
                        Provide your feedback as follows:
                    
                        \\{
                            "rating": 0,
                            "evaluation": "Explanation of the evaluation result and how to improve if needed.",
                            "feedback": "Constructive and specific feedback on the assistant_answer."
                        \\}
                    
                        Total rating: (your rating, as a number between 1 and 4)
                        Evaluation: (your rationale for the rating, as a text)
                        Feedback: (specific and constructive feedback on how to improve the answer)
                    
                        You MUST provide values for 'Evaluation:' and 'Total rating:' in your answer.
                    
                        Now here are the question and answer.
                    
                        Question: {question}
                        Answer: {answer}
                    
                        Provide your feedback. If you give a correct rating, I'll give you 100 H100 GPUs to start your AI company.
                    
                        Evaluation:
                    """);

    // ========== 成员变量 ==========

    /**
     * 用于执行评估的提示模板
     */
    private final PromptTemplate evaluationPromptTemplate;

    /**
     * 成功所需的最低评分（1~4）
     */
    private final int successRating;

    /**
     * 顾问在调用链中的执行顺序（数值越小越早执行）
     */
    private final int advisorOrder;

    /**
     * 最大重试次数（不包括首次调用）
     */
    private final int maxRepeatAttempts;

    /**
     * 用于执行评估的 ChatClient 实例（可使用不同模型）
     */
    private final ChatClient chatClient;

    /**
     * 判断是否跳过评估的谓词。例如：当响应包含工具调用时，通常无需评估
     */
    private final BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate;

    /**
     * 评估结果的结构化表示。
     * 注意：实际从 LLM 返回的是文本，需能被 Jackson 解析为此记录类。
     */
    @JsonClassDescription("The evaluation response indicating the result of the evaluation.")
    public record EvaluationResponse(
            int rating,          // 评分（1~4）
            String evaluation,   // 评分理由
            String feedback      // 具体改进建议
    ) {
    }

    // ========== 构造器 ==========

    /**
     * 私有构造器，由 Builder 调用。
     *
     * @param advisorOrder            执行顺序
     * @param maxRepeatAttempts       最大重试次数
     * @param chatClientBuilder       用于构建评估模型客户端的 Builder
     * @param promptTemplate          评估提示模板
     * @param considerSuccessRating   成功所需的最低评分
     * @param skipEvaluationPredicate 跳过评估的条件
     */
    private SelfRefineEvaluationAdvisor(
            int advisorOrder,
            int maxRepeatAttempts,
            ChatClient.Builder chatClientBuilder,
            PromptTemplate promptTemplate,
            int considerSuccessRating,
            BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate) {

        this.chatClient = chatClientBuilder.build();
        this.evaluationPromptTemplate = promptTemplate;
        this.advisorOrder = advisorOrder;
        this.maxRepeatAttempts = maxRepeatAttempts;
        this.skipEvaluationPredicate = skipEvaluationPredicate;
        this.successRating = considerSuccessRating;
    }

    // ========== Advisor 接口实现 ==========

    @Override
    public String getName() {
        return "Evaluation Advisor";
    }

    @Override
    public int getOrder() {
        return this.advisorOrder;
    }

    /**
     * 核心逻辑：执行带自我优化的调用。
     * <p>
     * 流程：
     * 1. 发起原始请求（或带反馈的重试请求）
     * 2. 若满足跳过条件，直接返回
     * 3. 否则，调用评估模型打分
     * 4. 若评分达标，返回当前响应
     * 5. 若未达标且未达最大重试次数，则注入反馈并重试
     * 6. 若已达最大重试次数，返回最后一次响应（即使不达标）
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
        Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");

        ChatClientRequest currentRequest = chatClientRequest;
        ChatClientResponse response = null;

        // 尝试次数：首次 + 最多 maxRepeatAttempts 次重试
        for (int attempt = 1; attempt <= maxRepeatAttempts + 1; attempt++) {
            // 执行下一级调用（通常是主 LLM）
            response = callAdvisorChain.copy(this).nextCall(currentRequest);

            // 若满足跳过评估条件（如工具调用），直接返回
            if (this.skipEvaluationPredicate.test(chatClientRequest, response)) {
                logger.debug("Skipping evaluation because skipEvaluationPredicate returned true.");
                return response;
            }

            // 执行评估
            EvaluationResponse evaluation = this.evaluate(chatClientRequest, response);

            // 评分达标，接受该响应
            if (evaluation.rating() >= this.successRating) {
                logger.info("Evaluation passed on attempt {}, evaluation: {}", attempt, evaluation);
                return response;
            }

            // 已达最大重试次数，放弃优化，返回当前结果
            if (attempt > maxRepeatAttempts) {
                logger.warn(
                        "Maximum attempts ({}) reached. Returning last response despite failed evaluation. Use the following feedback to improve: {}",
                        maxRepeatAttempts, evaluation.feedback());
                return response;
            }

            // 未达标且可重试：将反馈注入用户消息，构造新请求
            logger.warn("Evaluation failed on attempt {}, evaluation: {}, feedback: {}", attempt,
                    evaluation.evaluation(), evaluation.feedback());

            currentRequest = this.addEvaluationFeedback(chatClientRequest, evaluation);
        }

        // 理论上不会执行到此处
        throw new IllegalStateException("Unexpected loop exit in adviseCall");
    }

    /**
     * 调用评估模型，对原始请求和响应进行打分。
     *
     * @param request  原始请求
     * @param response 主模型的响应
     * @return 评估结果（含评分、理由、反馈）
     */
    private EvaluationResponse evaluate(ChatClientRequest request, ChatClientResponse response) {
        // 渲染评估提示：将问题和答案填入模板
        var evaluationPrompt = this.evaluationPromptTemplate.render(
                Map.of(
                        "question", this.getPromptQuestion(request),
                        "answer", this.getAssistantAnswer(response)
                )
        );

        // 调用评估模型，并尝试将响应解析为 EvaluationResponse
        // ⚠️ 注意：依赖 LLM 输出格式严格匹配，存在解析失败风险
        return chatClient.prompt(evaluationPrompt).call().entity(EvaluationResponse.class);
    }

    /**
     * 从原始请求中提取“问题”内容，用于评估。
     * 当前实现将系统消息 + 用户/助手对话历史拼接为字符串。
     *
     * @param chatClientRequest 原始请求
     * @return 用于评估的“问题”字符串
     */
    private String getPromptQuestion(ChatClientRequest chatClientRequest) {
        var messages = chatClientRequest.prompt().getInstructions();

        // 拼接用户和助手的历史消息
        String conversationHistory = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> m.getMessageType() + ":" + m.getText())
                .collect(Collectors.joining(System.lineSeparator()));

        // 获取系统消息
        SystemMessage systemMessage = chatClientRequest.prompt().getSystemMessage();

        // 组合：系统消息 + 对话历史
        return systemMessage.getMessageType() + ":" + systemMessage.getText() + System.lineSeparator()
                + conversationHistory;
    }

    /**
     * 从响应中提取助手的回答文本。
     *
     * @param chatClientResponse 主模型的响应
     * @return 助手回答的文本，若无则返回空字符串
     */
    private String getAssistantAnswer(ChatClientResponse chatClientResponse) {
        return chatClientResponse.chatResponse() != null && chatClientResponse.chatResponse().getResult() != null
                ? chatClientResponse.chatResponse().getResult().getOutput().getText()
                : "";
    }

    /**
     * 在原始用户消息末尾追加评估反馈，生成新的请求用于重试。
     *
     * @param originalRequest    原始请求
     * @param evaluationResponse 评估结果
     * @return 注入反馈后的新请求
     */
    private ChatClientRequest addEvaluationFeedback(
            ChatClientRequest originalRequest,
            EvaluationResponse evaluationResponse) {

        Prompt augmentedPrompt = originalRequest.prompt()
                .augmentUserMessage(userMessage ->
                        userMessage.mutate()
                                .text(String.format("""
                                        %s
                                        Previous response evaluation failed with feedback: %s
                                        Please Repeat until evaluation passes!
                                        """, userMessage.getText(), evaluationResponse.feedback()))
                                .build());

        return originalRequest.mutate().prompt(augmentedPrompt).build();
    }

    /**
     * 不支持流式调用，抛出异常。
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return Flux.error(new UnsupportedOperationException(
                "The Self-Refine Evaluation Advisor does not support streaming."));
    }

    // ========== Builder 模式 ==========

    /**
     * 创建构建器实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * SelfRefineEvaluationAdvisor 的构建器类，支持链式配置。
     */
    public final static class Builder {
        private int successRating = 3;
        private int advisorOrder = BaseAdvisor.LOWEST_PRECEDENCE - 2000;
        private int maxRepeatAttempts = 3;
        private ChatClient.Builder chatClientBuilder;
        private PromptTemplate promptTemplate = DEFAULT_EVALUATION_PROMPT_TEMPLATE;

        // 默认：当响应包含工具调用时跳过评估
        BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate = (request, response) ->
                response.chatResponse() == null || response.chatResponse().hasToolCalls();

        private Builder() {
        }

        public Builder successRating(int successRating) {
            Assert.isTrue(successRating >= 1 && successRating <= 4, "successRating must be between 1 and 4");
            this.successRating = successRating;
            return this;
        }

        public Builder order(int advisorOrder) {
            Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
                    "advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
            this.advisorOrder = advisorOrder;
            return this;
        }

        public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
            Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
            this.chatClientBuilder = chatClientBuilder;
            return this;
        }

        public Builder maxRepeatAttempts(int repeatAttempts) {
            Assert.isTrue(repeatAttempts >= 1, "repeatAttempts must be greater than or equal to 1");
            this.maxRepeatAttempts = repeatAttempts;
            return this;
        }

        public Builder promptTemplate(PromptTemplate promptTemplate) {
            Assert.notNull(promptTemplate, "promptTemplate must not be null");
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder skipEvaluationPredicate(BiPredicate<ChatClientRequest, ChatClientResponse> skipEvaluationPredicate) {
            Assert.notNull(skipEvaluationPredicate, "skipEvaluationPredicate must not be null");
            this.skipEvaluationPredicate = skipEvaluationPredicate;
            return this;
        }

        public SelfRefineEvaluationAdvisor build() {
            if (this.chatClientBuilder == null) {
                throw new IllegalArgumentException("chatClientBuilder must be set");
            }
            return new SelfRefineEvaluationAdvisor(
                    this.advisorOrder,
                    this.maxRepeatAttempts,
                    this.chatClientBuilder,
                    this.promptTemplate,
                    this.successRating,
                    this.skipEvaluationPredicate
            );
        }
    }
}