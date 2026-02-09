package com.wx.ai.learn.config;

import com.wx.ai.learn.advisor.MyLoggingAdvisor;
import com.wx.ai.learn.advisor.SelfRefineEvaluationAdvisor;
import com.wx.ai.learn.tool.WeatherTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Bean
    @Primary  // 设置为默认，不加 Qualifier 时优先选这个
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You are a helpful assistant.").defaultAdvisors().build();
    }

    @Bean
    public ChatClient weatherChatClient(ChatClient.Builder builder, // 注入 Spring Boot 自动配置的 Builder 基础实例
                                        ChatModel chatModel) {
        // 在此处集中配置通用的工具、顾问和模型
        return builder.defaultSystem("你是一个专业的气象助手。").defaultTools(new WeatherTool()) // 注册工具类
                .defaultAdvisors(
                        // 1. 自我修正评估顾问（使用本地 Ollama 模型进行评估）
                        SelfRefineEvaluationAdvisor.builder().chatClientBuilder(ChatClient.builder(chatModel)).maxRepeatAttempts(15).successRating(4).order(0).build(),
                        // 2. 自定义日志顾问
                        new MyLoggingAdvisor(2)).build();
    }

}