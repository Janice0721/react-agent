package com.reactagent.memory.api;

import com.reactagent.memory.model.MemorySummary;

import java.util.List;

/**
 * 中期记忆接口:会话级摘要,存储在 Redis 中。
 * <p>
 * 可扩展:后续可替换为其他缓存方案或中心化记忆服务。
 */
public interface MidTermMemory {

    /** 存储一条摘要到指定会话 */
    void store(String sessionId, MemorySummary summary);

    /** 获取指定会话的全部摘要(按时间正序) */
    List<MemorySummary> get(String sessionId);

    /** 获取指定会话摘要的拼接文本(用于注入上下文) */
    String getSummaryText(String sessionId);

    /** 清除指定会话的中期记忆 */
    void clear(String sessionId);
}
