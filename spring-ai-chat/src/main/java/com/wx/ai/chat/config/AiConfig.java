/*
 * Copyright (c) 2026 the original author or authors. All rights reserved.
 *
 * @author wangxu
 * @since 2026
 */
package com.wx.ai.chat.config;

import com.wx.ai.chat.advisor.MyLoggingAdvisor;
import com.wx.ai.chat.advisor.SelfRefineEvaluationAdvisor;
import com.wx.ai.chat.tool.WeatherTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You are a helpful assistant.").build();
    }

    // 1. 标准天气客户端：包含自我修正，用于 .call()
    @Bean
    public ChatClient weatherChatClient(ChatClient.Builder builder, ChatModel chatModel) {
        return builder
            .defaultSystem("你是一个专业的气象助手。")
            .defaultTools(new WeatherTool())
            .defaultAdvisors(
                // 自我修正顾问
                SelfRefineEvaluationAdvisor.builder()
                    .chatClientBuilder(ChatClient.builder(chatModel))
                    .maxRepeatAttempts(15)
                    .successRating(4)
                    .build(),
                new MyLoggingAdvisor(2)
            )
            .build();
    }

    // 2. 流式天气客户端：去掉自我修正，用于 .stream()
    // 注意：名字区分开
    @Bean
    public ChatClient weatherStreamingChatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("你是一个专业的气象助手。")
            .defaultTools(new WeatherTool()) // 依然可以调用天气工具
            .defaultAdvisors(new MyLoggingAdvisor(2)) // 仅保留日志，去掉 SelfRefine
            .build();
    }
}