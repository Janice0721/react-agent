package com.reactagent.memory.midterm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactagent.memory.api.MidTermMemory;
import com.reactagent.memory.config.MemoryProperties;
import com.reactagent.memory.entity.SessionSummaryRepository;
import com.reactagent.memory.model.MemorySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 中期记忆实现:会话级摘要,Redis 缓存 + MySQL 持久化。
 * <p>
 * 真相源在 MySQL(session_summary 表),Redis 仅作缓存:<br>
 *   - store: MySQL 落库 + 刷新 Redis(带 TTL)<br>
 *   - get: 先读 Redis,miss 回查 MySQL 并回填缓存<br>
 *   - clear: 删 MySQL + 删 Redis<br>
 * 这样 TTL 不再是"数据丢失定时器",缓存过期可从 MySQL 重建。<br>
 * Key: session:{sessionId}:midterm → 摘要列表 JSON
 */
@Component
public class MidTermMemoryImpl implements MidTermMemory {

    private static final Logger log = LoggerFactory.getLogger(MidTermMemoryImpl.class);
    private static final String KEY_PREFIX = "session:";
    private static final String KEY_SUFFIX = ":midterm";

    private final StringRedisTemplate redisTemplate;
    private final SessionSummaryRepository repository;
    private final MemoryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MidTermMemoryImpl(StringRedisTemplate redisTemplate,
                             SessionSummaryRepository repository,
                             MemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
        this.properties = properties;
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId + KEY_SUFFIX;
    }

    private Duration ttl() {
        Duration t = properties.getSessionSummaryTtl();
        return t != null ? t : Duration.ofDays(7);
    }

    @Override
    public void store(String sessionId, MemorySummary summary) {
        // 1. 落库 MySQL(真相源)
        repository.save(summary);
        // 2. 刷新 Redis 缓存(读现有库内全部 + 写回,保证缓存与库一致)
        try {
            List<MemorySummary> all = repository.findAll(sessionId);
            redisTemplate.opsForValue().set(key(sessionId),
                    objectMapper.writeValueAsString(all), ttl());
            log.debug("中期记忆存储: session={} 摘要数={}", sessionId, all.size());
        } catch (Exception e) {
            log.error("中期记忆刷新缓存失败(库已落): session={}", sessionId, e);
        }
    }

    @Override
    public List<MemorySummary> get(String sessionId) {
        // 1. 先读 Redis
        try {
            String json = redisTemplate.opsForValue().get(key(sessionId));
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<List<MemorySummary>>() {});
            }
        } catch (Exception e) {
            log.warn("中期记忆读缓存失败,回查 MySQL: session={}", sessionId);
        }
        // 2. miss 回查 MySQL 并回填缓存
        List<MemorySummary> all = repository.findAll(sessionId);
        if (!all.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(key(sessionId),
                        objectMapper.writeValueAsString(all), ttl());
            } catch (Exception e) {
                log.warn("中期记忆回填缓存失败: session={}", sessionId);
            }
        }
        return all;
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
    public String getWatermark(String sessionId) {
        return repository.getWatermark(sessionId);
    }

    @Override
    public void clear(String sessionId) {
        repository.deleteBySession(sessionId);
        redisTemplate.delete(key(sessionId));
        log.info("清除中期记忆: session={}", sessionId);
    }
}
