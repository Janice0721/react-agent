package com.reactagent.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆模块配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.memory")
public class MemoryProperties {

    /** 短期记忆:组装上下文时从 DB 取最近多少条消息 */
    private int shortTermLimit = 20;

    /** 短期记忆:token 超过此阈值触发压缩 */
    private int compressThreshold = 8000;

    /** 短期记忆:压缩时保留最近几条原文 */
    private int compressKeepRecent = 6;

    /** 长期记忆:检索 top-k */
    private int longTermTopK = 5;

    /** Qdrant 配置 */
    private Qdrant qdrant = new Qdrant();

    @Data
    public static class Qdrant {
        /** Qdrant 服务地址 */
        private String host = "localhost";
        /** Qdrant gRPC 端口 */
        private int port = 6334;
        /** Collection 名称 */
        private String collectionName = "react_agent_memory";
        /** 向量维度(需与 embedding 模型一致) */
        private int vectorDimension = 1536;
        /** 是否启用长期记忆(Qdrant 未启动时设为 false) */
        private boolean enabled = true;
    }
}
