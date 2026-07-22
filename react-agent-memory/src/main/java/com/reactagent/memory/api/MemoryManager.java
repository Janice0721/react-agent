package com.reactagent.memory.api;

import com.reactagent.core.msg.Msg;

import java.util.List;

/**
 * 记忆总管:组合三层记忆,提供统一的上下文组装能力。
 * <p>
 * ReAct 引擎只依赖此接口,不直接访问底层存储。
 * 后续可演进为中心化记忆系统(Memory Service)。
 */
public interface MemoryManager {

    /** 写入短期记忆(消息落库) */
    void addShortTerm(Msg msg);

    /**
     * 组装上下文:短期原文 + 中期摘要 + 长期检索注入。
     * 引擎每次推理前调用此方法获取完整上下文。
     */
    List<Msg> buildContext(String sessionId, String userId, String currentQuery);

    /** 获取指定会话全部历史(用于恢复) */
    List<Msg> loadHistory(String sessionId);

    /** 估算当前会话 token 数 */
    int estimateTokens(String sessionId);

    /**
     * 记忆沉淀:会话结束时,将关键信息提取为长期记忆。
     */
    void consolidate(String sessionId, String userId, Msg finalReply);

    /** 清除指定会话全部记忆(短/中/长) */
    void clearSession(String sessionId, String userId);
}
