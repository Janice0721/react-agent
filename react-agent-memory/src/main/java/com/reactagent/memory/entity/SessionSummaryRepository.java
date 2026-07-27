package com.reactagent.memory.entity;

import com.reactagent.memory.model.MemorySummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话摘要持久化 Repository(中期记忆落库)。
 * <p>
 * 压缩产出的摘要写此表 + Redis 缓存;agent_message 原文永不删除。
 * 水位线 = 最近一次摘要的 to_time。
 */
@Repository
public class SessionSummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MemorySummary> ROW_MAPPER = (rs, rowNum) -> {
        MemorySummary s = new MemorySummary();
        s.setId(rs.getString("id"));
        s.setSessionId(rs.getString("session_id"));
        s.setSummary(rs.getString("summary"));
        s.setKeyPoints(rs.getString("key_points"));
        s.setFromTime(rs.getString("from_time"));
        s.setToTime(rs.getString("to_time"));
        s.setCreatedAt(rs.getString("created_at"));
        return s;
    };

    public SessionSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入一条摘要 */
    public void save(MemorySummary s) {
        jdbcTemplate.update(
            "INSERT INTO session_summary (id, session_id, summary, key_points, " +
            "from_time, to_time, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            s.getId(), s.getSessionId(), s.getSummary(), s.getKeyPoints(),
            s.getFromTime(), s.getToTime(), s.getCreatedAt()
        );
    }

    /** 查询会话全部摘要(按创建时间正序) */
    public List<MemorySummary> findAll(String sessionId) {
        String sql = "SELECT * FROM session_summary WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, sessionId);
    }

    /** 获取水位线:最近一次摘要覆盖到的消息 created_at(to_time) */
    public String getWatermark(String sessionId) {
        List<String> rs = jdbcTemplate.queryForList(
            "SELECT to_time FROM session_summary WHERE session_id = ? " +
            "ORDER BY created_at DESC LIMIT 1",
            String.class, sessionId);
        return rs.isEmpty() ? null : rs.get(0);
    }

    /** 删除会话全部摘要 */
    public void deleteBySession(String sessionId) {
        jdbcTemplate.update("DELETE FROM session_summary WHERE session_id = ?", sessionId);
    }
}
