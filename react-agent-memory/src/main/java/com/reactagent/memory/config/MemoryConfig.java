package com.reactagent.memory.config;

import com.reactagent.memory.longterm.QdrantLongTermMemory;
import com.reactagent.model.ModelAdapter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆模块 Spring 配置。
 * <p>
 * 创建 QdrantClient Bean 和 QdrantLongTermMemory Bean。
 * 如果 Qdrant 未启动,长期记忆功能降级(不影响短期/中期)。
 */
@Configuration
public class MemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryConfig.class);

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(MemoryProperties properties) {
        MemoryProperties.Qdrant qdrantConfig = properties.getQdrant();
        log.info("初始化 Qdrant 客户端: {}:{} collection={}",
                qdrantConfig.getHost(), qdrantConfig.getPort(),
                qdrantConfig.getCollectionName());

        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(
                qdrantConfig.getHost(), qdrantConfig.getPort(), false
        ).build();
        return new QdrantClient(grpcClient);
    }

    @Bean
    public QdrantLongTermMemory longTermMemory(
            QdrantClient qdrantClient,
            ModelAdapter modelAdapter,
            MemoryProperties properties) {
        QdrantLongTermMemory ltm = new QdrantLongTermMemory(
                qdrantClient,
                modelAdapter,
                properties.getQdrant().getCollectionName(),
                properties.getQdrant().getVectorDimension()
        );

        if (properties.getQdrant().isEnabled()) {
            try {
                ltm.init();
            } catch (Exception e) {
                log.warn("Qdrant 初始化失败,长期记忆功能将降级: {}", e.getMessage());
            }
        } else {
            log.info("长期记忆(Qdrant)已禁用");
        }

        return ltm;
    }
}
