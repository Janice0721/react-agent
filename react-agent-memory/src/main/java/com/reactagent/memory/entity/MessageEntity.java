package com.reactagent.memory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息持久化实体(对应 agent_message 表)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEntity {
    private String id;
    private String sessionId;
    private String role;        // USER / ASSISTANT / SYSTEM
    private String name;
    private String contentJson; // ContentBlock 列表的 JSON 序列化
    private String metadataJson;
    private String createdAt;
    private String finishedAt;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
