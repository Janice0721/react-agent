package com.reactagent.memory.shortterm;

import com.reactagent.core.msg.Msg;
import com.reactagent.memory.api.ShortTermMemory;
import com.reactagent.memory.entity.MessageRepository;
import com.reactagent.memory.entity.MessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 短期记忆(消息历史)实现:按会话维度落库 MySQL。
 * <p>
 * 这是 append-only 的最终消息历史,用于溯源/回放/审计,<b>永不删除</b>。
 * 压缩只推进中期记忆的水位线,原文留存。
 * 可扩展:后续可叠加内存缓存层或换为分布式存储。
 */
@Component
public class ShortTermMemoryImpl implements ShortTermMemory {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemoryImpl.class);

    private final MessageRepository repository;

    public ShortTermMemoryImpl(MessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(Msg msg) {
        if (msg == null || msg.getSessionId() == null) {
            log.warn("消息或 sessionId 为空,跳过短期记忆写入");
            return;
        }
        MessageEntity entity = MsgSerializer.toEntity(msg);
        repository.save(entity);
        log.debug("短期记忆写入: session={} msgId={} role={}",
                msg.getSessionId(), msg.getId(), msg.getRole());
    }

    @Override
    public List<Msg> getRecent(String sessionId, int limit) {
        return repository.findRecent(sessionId, limit).stream()
                .map(MsgSerializer::toMsg)
                .toList();
    }

    @Override
    public List<Msg> getAll(String sessionId) {
        return repository.findAll(sessionId).stream()
                .map(MsgSerializer::toMsg)
                .toList();
    }

    @Override
    public int count(String sessionId) {
        return repository.count(sessionId);
    }

    @Override
    public int estimateTokens(String sessionId) {
        // 粗略估算:content 字符数 / 4(走 DB 聚合,避免全量加载)
        return repository.sumContentLength(sessionId) / 4;
    }

    @Override
    public List<Msg> takeOldMessages(String sessionId, int keepRecent) {
        return repository.findOld(sessionId, keepRecent).stream()
                .map(MsgSerializer::toMsg)
                .toList();
    }

    @Override
    public List<Msg> getAfter(String sessionId, String watermark) {
        return repository.findAfter(sessionId, watermark).stream()
                .map(MsgSerializer::toMsg)
                .toList();
    }

    @Override
    public void clear(String sessionId) {
        repository.deleteBySession(sessionId);
        log.info("清除短期记忆: session={}", sessionId);
    }
}
