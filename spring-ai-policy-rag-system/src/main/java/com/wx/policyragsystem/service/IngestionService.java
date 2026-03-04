package com.wx.policyragsystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionService {
    private final VectorStore vectorStore;

    public void processDocument(MultipartFile file) throws IOException {
        // Tika 解析
        TikaDocumentReader loader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        // 切片 (TokenSplitter 防止语义切断)
        TextSplitter splitter = new TokenTextSplitter(500, 100, 10, 10000, true);
        List<Document> splitDocuments = splitter.apply(loader.get());

        // 元数据注入
        splitDocuments.forEach(doc -> doc.getMetadata().put("filename", file.getOriginalFilename()));
        // 入库
        vectorStore.add(splitDocuments);
    }
}
