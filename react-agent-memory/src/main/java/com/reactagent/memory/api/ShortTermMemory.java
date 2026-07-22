package com.reactagent.memory.api;

import com.reactagent.core.msg.Msg;

import java.util.List;

/**
 * 短期记忆接口:最近对话记录,按会话维度持久化到数据库。
 * <p>
 * 可扩展:后续可替换为分布式缓存或外部记忆服务。
 */
public interface ShortTermMemory {

    /** 添加一条消息到当前会话的短期记忆 */
    void add(Msg msg);

    /** 获取指定会话最近 N 条消息(按时间正序) */
    List<Msg> getRecent(String sessionId, int limit);

    /** 获取指定会话全部消息 */
    List<Msg> getAll(String sessionId);

    /** 获取指定会话消息总数 */
    int count(String sessionId);

    /** 估算指定会话的 token 数(粗略:字符数/4) */
    int estimateTokens(String sessionId);

    /** 取出旧消息用于压缩(保留最近 keepRecent 条,返回其余) */
    List<Msg> takeOldMessages(String sessionId, int keepRecent);

    /** 按会话维度清除短期记忆 */
    void clear(String sessionId);
}
