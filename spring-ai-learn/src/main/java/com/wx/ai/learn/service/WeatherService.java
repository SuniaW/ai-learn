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

@Service
public class WeatherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherService.class);
    private final ChatClient genericClient;
    private final ChatClient weatherClient;

    // 通过 @Qualifier 指定 Bean 的名称
    public WeatherService(
            @Qualifier("chatClient") ChatClient genericClient,
            @Qualifier("weatherChatClient") ChatClient weatherClient) {
        this.genericClient = genericClient;
        this.weatherClient = weatherClient;
    }

    public String doWork(String city) {
        //没有使用Function
        String common = genericClient.prompt("你好").call().content();
        //使用Function
        String weather = this.weatherClient
                .prompt("请查询 " + city + " 的天气，并友好地回复用户。")
                .call()
                .content();
        LOGGER.info("common response: {}", common);
        LOGGER.info("weather response: {}", weather);
        return common + weather;
    }
}
