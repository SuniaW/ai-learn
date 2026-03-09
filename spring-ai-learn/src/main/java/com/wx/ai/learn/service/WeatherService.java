/*
 * Copyright (c) 2026 the original author or authors. All rights reserved.
 *
 * @author wangxu
 * @since 2026
 */
package com.wx.ai.learn.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class WeatherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherService.class);
    private final ChatClient genericClient;
    private final ChatClient weatherClient;
    private final ChatClient streamingClient; // 专门用于流式的 Client



    public WeatherService(ChatClient genericClient, @Qualifier("weatherChatClient") ChatClient weatherClient,
        @Qualifier("weatherStreamingChatClient") ChatClient streamingClient) {
        this.genericClient = genericClient;
        this.weatherClient = weatherClient;
        this.streamingClient = streamingClient;
    }



    public String doChat(String key){
        //没有使用Function
        return genericClient.prompt(key).call().content();
    }

    public String doWork(String city) {
        //使用Function
        return this.weatherClient
                .prompt("请查询 " + city + " 的天气，并友好地回复用户。")
                .call()
                .content();
    }

    // 2. 新的流式方法（使用 streamingClient，避开报错的 Advisor）
    public Flux<String> doWorkStream(String city) {
        return this.streamingClient.prompt()
            .user(u -> u.text("你好！请查询 {city} 的天气，并以友好的态度回复用户。").param("city", city)).stream() // 流式请求
            .content();
    }
}
