package com.reactagent.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 长期记忆检索结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemorySearchResult {
    /** 记忆 ID */
    private String id;
    /** 内容文本 */
    private String content;
    /** 相似度分数(0~1,越高越相似) */
    private float score;
    /** 用户 ID */
    private String userId;
    /** 来源会话 ID */
    private String sessionId;
    /** 创建时间 */
    private String createdAt;
}
