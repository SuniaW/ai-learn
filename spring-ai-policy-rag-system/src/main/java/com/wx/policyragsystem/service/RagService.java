package com.wx.policyragsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // 系统提示词：强化对排版格式的要求
    private static final String SYSTEM_PROMPT = """
        你是一位专业的软件架构师。请基于提供的背景资料回答问题。
        输出要求：
        1. **结构清晰**：使用标准 Markdown 格式。
        2. **强制换行**：在每个标题（#、##、###）前必须空出一行。
        3. **代码规范**：所有代码必须使用三反引号包裹并标明语言（如 ```xml 或 ```java），代码内要有关键注释。
        4. **禁止编造**：背景资料中未提及的内容，请诚实告知。
        5. **视觉优化**：多使用列表（- 或 1.）来拆解复杂步骤。
        """;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
            .build();
    }

    public Flux<String> streamAnswer(String query, String chatId) {
        return Flux.defer(() -> {
            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.4) // 调低一点阈值以获得更多参考内容
                .build();

            List<Document> docs;
            try {
                docs = vectorStore.similaritySearch(searchRequest);
            } catch (Exception e) {
                log.error("检索异常", e);
                return Flux.just("⚠️ 知识库检索失败。");
            }

            if (docs.isEmpty()) {
                return Flux.just("🔍 未找到相关技术文档。");
            }

            String references = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("filename", "未知来源"))
                .distinct()
                .collect(Collectors.joining(", "));

            String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

            return chatClient.prompt()
                .user(u -> u.text("问题：{query}\n\n背景资料：\n{context}")
                    .param("query", query)
                    .param("context", context))
                .advisors(a -> a.param(MessageChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_CONVERSATION_ID, chatId))
                .stream()
                .content()
                .concatWith(Flux.just("\n\n---\n> 📚 **参考文件：** " + references))
                .onErrorResume(e -> {
                    log.error("AI 生成异常", e);
                    return Flux.just("\n\n⚠️ [对话中断] 生成过程出现异常，请重试。");
                });
        });
    }
}