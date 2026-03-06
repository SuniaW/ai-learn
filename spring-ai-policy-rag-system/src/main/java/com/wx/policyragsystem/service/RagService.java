package com.wx.policyragsystem.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document; // 确保导包正确！
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;


@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore; // 使用接口类型

    // Spring 会自动注入配置好的 VectorStore 实现类 (MilvusVectorStore)
    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamAnswer(String query) {
        // 1. 向量数据库相似度检索
        // 注意：确保 application.yml 中配置了正确的 collection-name
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(3)
                .similarityThreshold(0.1) // 阈值可根据需要调整
                .build()
        );

        if (docs.isEmpty()) {
            return Flux.just("未找到相关政策信息。");
        }

        // 2. 组装上下文与引用来源
        String context = docs.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));

        String refs = docs.stream()
            .map(d -> (String) d.getMetadata().getOrDefault("filename", "未知来源"))
            .distinct()
            .collect(Collectors.joining(", "));

        // 3. 构建 Prompt
        String prompt = "基于以下政策上下文回答问题：\n" + context + "\n\n问题：" + query;

        // 4. 流式返回
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content()
            .concatWith(Flux.just("\n\n---\n📚 来源: " + refs));
    }

}