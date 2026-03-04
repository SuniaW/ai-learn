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
    private final VectorStore vectorStore;

    // 推荐在构造时直接构建好 ChatClient 实例
    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public Flux<String> streamAnswer(String query) {

        // 1. 向量数据库相似度检索 (Top 3，阈值 0.6)
        // 使用新版的 Builder 模式
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .similarityThreshold(0.6)
                        .build()
        );

        // 2. 组装上下文与引用来源
        String context = docs.stream()
                .map(Document::getText)    // ✅ 改为 getText() 即可
                .collect(Collectors.joining("\n\n"));

        String refs = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("filename", "未知来源"))
                .distinct()
                .collect(Collectors.joining(", "));

        // 3. 构建 Prompt
        String prompt = "基于以下政策上下文回答问题：\n" + context + "\n\n问题：" + query;

        // 4. 流式(SSE)返回内容，并在末尾拼接来源参考
        // 新版 ChatClient 推荐使用 .prompt().user(prompt) 语法
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .concatWith(Flux.just("\n\n---\n📚 来源: " + refs));
    }
}