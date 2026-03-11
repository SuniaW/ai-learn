package com.wx.policyragsystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    private final VectorStore vectorStore;

    public void processDocuments(MultipartFile[] files) {
        List<Document> allSplitDocuments = new ArrayList<>();

        // 1. 批量解析和切片
        for (MultipartFile file : files) {
            try {
                log.info("正在解析文件: {}", file.getOriginalFilename());
                TikaDocumentReader loader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));

                // 建议切片大小根据模型调整，500 tokens 左右通常比较平衡
                TextSplitter splitter = new TokenTextSplitter(600, 120, 10, 10000, true);
                List<Document> documents = loader.get();

                // 注入元数据
                documents.forEach(doc -> doc.getMetadata().put("filename", file.getOriginalFilename()));

                // 执行切片并加入汇总列表
                allSplitDocuments.addAll(splitter.apply(documents));

            } catch (IOException e) {
                log.error("解析文件 {} 失败", file.getOriginalFilename(), e);
            }
        }

        // 2. 统一入库（批量操作性能远高于循环单次入库）
        if (!allSplitDocuments.isEmpty()) {
            log.info("开始批量入库，总片段数: {}", allSplitDocuments.size());
            vectorStore.add(allSplitDocuments);
            log.info("入库完成");
        }
    }
}