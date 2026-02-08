package com.wx.ai.learn.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class WeatherTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherTool.class);

    @Tool(description = "根据城市名称查询当前天气状况")
    public String getWeather(String cityName) {
        // 这里模拟调用外部天气API
        LOGGER.info("正在查询{}的天气...",cityName);
        return "晴朗，气温 25 摄氏度";
    }
}