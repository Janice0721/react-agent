package com.reactagent.memory.api;

import com.reactagent.core.msg.Msg;

import java.util.List;

/**
 * 长期记忆接口:跨会话知识持久化 + 向量语义检索。
 * <p>
 * 当前实现基于 Qdrant,可扩展为其他向量数据库或中心化记忆服务。
 */
public interface LongTermMemory {

    /** 存储一条长期记忆(自动向量化) */
    void store(Msg msg, String userId);

    /** 语义检索:根据查询文本返回最相似的 top-k 条记忆 */
    List<Msg> search(String query, String userId, int topK);

    /** 获取用户全部长期记忆 */
    List<Msg> getAll(String userId);

    /** 删除指定记忆 */
    void delete(String memoryId);

    /** 清除用户全部长期记忆 */
    void clear(String userId);
}
