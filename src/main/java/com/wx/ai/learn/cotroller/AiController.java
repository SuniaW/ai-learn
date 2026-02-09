package com.wx.ai.learn.cotroller;

import com.wx.ai.learn.service.AiService;
import com.wx.ai.learn.service.WeatherService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiController.class);
    @Resource
    private AiService aiService;

    @Resource
    private WeatherService weatherService;

    private final OpenAiChatModel chatModel;

    @Autowired
    public AiController(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }


    @GetMapping("/weather")
    public String weather(@RequestParam(value = "city", defaultValue = "Paris") String city) {
        return weatherService.doWork(city);
    }

    @GetMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(
            @RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return chatModel.stream(new Prompt(new UserMessage(message)));
    }
}


