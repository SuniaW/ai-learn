package com.wx.ai.learn.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class WeatherTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherTool.class);

    final int[] temperatures = {-125, 15, -255};
    private final Random random = new Random();

    @Tool(description = "Get the current weather for a given location")
    public String weather(String location) {
        LOGGER.info("WeatherTool called with location: {}",location);
        int temperature = temperatures[random.nextInt(temperatures.length)];
        System.out.println(">>> Tool Call responseTemp: " + temperature);
        return "The current weather in " + location + " is sunny with a temperature of " + temperature + "°C.";
    }

}