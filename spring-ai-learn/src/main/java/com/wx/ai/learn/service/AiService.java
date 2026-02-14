/*
 * Copyright (c) 2026 the original author or authors. All rights reserved.
 *
 * @author wangxu
 * @since 2026
 */
package com.wx.ai.learn.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {


    private final ChatClient chatClient;

    // 通过构造函数注入 Builder 并立即构建 Client
    public AiService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String ask(String question) {
        return chatClient.prompt(question)
                .advisors(advisorSpec -> advisorSpec.param("city", question)) // 传递参数
                .call()
                .content();
    }
}