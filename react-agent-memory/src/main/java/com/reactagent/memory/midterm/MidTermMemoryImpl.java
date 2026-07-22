package com.reactagent.memory.midterm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactagent.memory.api.MidTermMemory;
import com.reactagent.memory.model.MemorySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 中期记忆实现:Redis 存储,会话级摘要。
 * <p>
 * Key 设计:
 * <ul>
 *   <li>session:{sessionId}:midterm → 摘要列表 JSON</li>
 * </ul>
 * TTL: 24 小时(可配置)。
 */
@Component
public class MidTermMemoryImpl implements MidTermMemory {

    private static final Logger log = LoggerFactory.getLogger(MidTermMemoryImpl.class);
    private static final String KEY_PREFIX = "session:";
    private static final String KEY_SUFFIX = ":midterm";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MidTermMemoryImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId + KEY_SUFFIX;
    }

    @Override
    public void store(String sessionId, MemorySummary summary) {
        try {
            String k = key(sessionId);
            // 读取现有列表
            List<MemorySummary> list = get(sessionId);
            list.add(summary);
            // 写回
            String json = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(k, json, DEFAULT_TTL);
            log.debug("中期记忆存储: session={} 摘要数={}", sessionId, list.size());
        } catch (Exception e) {
            log.error("中期记忆存储失败: session={}", sessionId, e);
        }
    }

    @Override
    public List<MemorySummary> get(String sessionId) {
        try {
            String k = key(sessionId);
            String json = redisTemplate.opsForValue().get(k);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<MemorySummary>>() {});
        } catch (Exception e) {
            log.error("中期记忆读取失败: session={}", sessionId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public String getSummaryText(String sessionId) {
        List<MemorySummary> summaries = get(sessionId);
        if (summaries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("[会话历史摘要]\n");
        for (MemorySummary s : summaries) {
            sb.append("- ").append(s.getSummary());
            if (s.getKeyPoints() != null && !s.getKeyPoints().isBlank()) {
                sb.append(" (关键点: ").append(s.getKeyPoints()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public void clear(String sessionId) {
        redisTemplate.delete(key(sessionId));
        log.info("清除中期记忆: session={}", sessionId);
    }
}
