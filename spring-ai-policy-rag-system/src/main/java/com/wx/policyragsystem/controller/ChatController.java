package com.wx.policyragsystem.controller;

import com.wx.policyragsystem.service.IngestionService;
import com.wx.policyragsystem.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 允许前端跨域
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;
    private final IngestionService ingestionService;

    // 流式问答接口 (SSE)
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String query,  @RequestParam(required = false) String chatId) {
        return ragService.streamAnswer(query, chatId);
    }

    // 文档上传接口
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        ingestionService.processDocument(file);
        return "上传并解析成功: " + file.getOriginalFilename();
    }
}