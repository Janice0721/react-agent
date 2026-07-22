package com.reactagent.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 记忆摘要:旧消息压缩后的结构化摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemorySummary {
    /** 摘要 ID */
    private String id;
    /** 所属会话 */
    private String sessionId;
    /** 摘要文本 */
    private String summary;
    /** 涵盖的消息时间范围(起) */
    private String fromTime;
    /** 涵盖的消息时间范围(止) */
    private String toTime;
    /** 关键事实/决策点(分号分隔) */
    private String keyPoints;
    /** 创建时间 */
    private String createdAt;
}
