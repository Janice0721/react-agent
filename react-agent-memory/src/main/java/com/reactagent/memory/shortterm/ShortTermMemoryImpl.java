package com.reactagent.memory.shortterm;

import com.reactagent.core.msg.Msg;
import com.reactagent.memory.api.ShortTermMemory;
import com.reactagent.memory.entity.MessageEntity;
import com.reactagent.memory.entity.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 短期记忆实现:最近对话记录,按会话维度落库 MySQL。
 * <p>
 * 内存中不缓存(直接走 DB),保证重启后可恢复。
 * 可扩展:后续可加内存缓存层或换为分布式缓存。
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
        // 粗略估算:字符数 / 4
        List<MessageEntity> all = repository.findAll(sessionId);
        int chars = 0;
        for (MessageEntity e : all) {
            if (e.getContentJson() != null) chars += e.getContentJson().length();
        }
        return chars / 4;
    }

    @Override
    public List<Msg> takeOldMessages(String sessionId, int keepRecent) {
        return repository.findOld(sessionId, keepRecent).stream()
                .map(MsgSerializer::toMsg)
                .toList();
    }

    @Override
    public void clear(String sessionId) {
        repository.deleteBySession(sessionId);
        log.info("清除短期记忆: session={}", sessionId);
    }
}
