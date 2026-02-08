package com.wx.ai.learn.cotroller;

import com.wx.ai.learn.service.AiService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
public class AiController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiController.class);
    @Resource
    private AiService aiService;

    @GetMapping("/weather")
    public String weather(@RequestParam(value = "city", defaultValue = "北京") String city) {
        // 1. 准备提示词
        String prompt = "请查询 " + city + " 的天气，并友好地回复用户。";
        // 2. 调用模型，模型会自动识别并调用 getWeather 工具
        return aiService.ask(prompt);
    }
}