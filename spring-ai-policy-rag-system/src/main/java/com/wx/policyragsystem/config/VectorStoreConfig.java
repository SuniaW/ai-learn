package com.wx.policyragsystem.config;

/**
 * @author wangxu
 * @version 1.0
 * @date 2026/3/6
 * @description VectorStoreConfig
 */

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(MilvusVectorStoreProperties.class)

public class VectorStoreConfig {
    private  static final Logger  LOGGER = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Value("${spring.ai.vectorstore.milvus.client.host}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.client.port}")
    private int port;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.milvus.embedding-dimension}")
    private int dimension;


    @Bean
    public MilvusServiceClient milvusClient() {
        return new MilvusServiceClient(
            ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build()
        );
    }

    @Bean
    @Primary
    public MilvusVectorStore vectorStore(MilvusServiceClient client, EmbeddingModel model) {
        LOGGER.info(">>>>>>>>>> [CRITICAL] 正在加载配置：集合名={}维度={}" ,collectionName, dimension);
        return MilvusVectorStore.builder(client, model)
            .collectionName(collectionName) // 使用从 YAML 读取的变量
            .embeddingDimension(dimension)  // 使用从 YAML 读取的变量
            .initializeSchema(true)
            .build();
    }
}