package com.wx.policyragsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultSystem(
                "你是一位专业的政策解读专家。请基于提供的背景资料回答用户问题。" + "如果背景资料中没有相关信息，请诚实告知。回答请使用 Markdown 格式，保持条理清晰。")
            .build();
    }

    public Flux<String> streamAnswer(String query) {
        // 使用 Flux.defer 确保所有代码都在响应式链条中，报错也会被捕获
        return Flux.defer(() -> {
            log.info("开始处理请求: {}", query);

            // 1. 在流内部执行检索，这样报错也会变成流的一部分，不会报 500
            List<Document> docs;
            try {
                docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(3).similarityThreshold(0.6).build());
            } catch (Exception e) {
                log.error("Milvus 检索阶段崩溃: ", e);
                return Flux.just("⚠️ 政策库暂时无法访问，请稍后再试。");
            }

            if (docs.isEmpty()) {
                return Flux.just("🔍 未找到相关政策。");
            }

            String references =
                docs.stream().map(d -> (String)d.getMetadata().getOrDefault("filename", "未知文件")).distinct()
                    .collect(Collectors.joining(", "));

            // 2. 发起 AI 对话
            return chatClient.prompt().user(query)
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder().query(query).topK(3).build()))
                .stream().content().timeout(Duration.ofSeconds(60)) // 设置 60s 超时保护
                .concatWith(Flux.just("\n\n---\n> 📚 **参考政策：** " + references)).onErrorResume(e -> {
                    log.error("AI 生成阶段异常: ", e);
                    return Flux.just("\n\n⚠️ [对话已安全断开] 服务器响应超时，请重试。");
                });
        }).doOnCancel(() -> log.info("连接已彻底断开，资源释放。")).onErrorResume(e -> {
            // 这是最后的兜底，确保无论如何都不向前端吐出 HTTP 500
            log.error("全局流异常兜底: ", e);
            return Flux.just("服务繁忙，请稍后再试。");
        });
    }
}