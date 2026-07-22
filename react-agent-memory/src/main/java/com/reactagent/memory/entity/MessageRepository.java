package com.reactagent.memory.entity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 消息持久化 Repository,基于 Spring JdbcTemplate。
 */
@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MessageEntity> ROW_MAPPER = (rs, rowNum) -> {
        MessageEntity e = new MessageEntity();
        e.setId(rs.getString("id"));
        e.setSessionId(rs.getString("session_id"));
        e.setRole(rs.getString("role"));
        e.setName(rs.getString("name"));
        e.setContentJson(rs.getString("content"));
        e.setMetadataJson(rs.getString("metadata"));
        e.setCreatedAt(rs.getString("created_at"));
        e.setFinishedAt(rs.getString("finished_at"));
        e.setPromptTokens(rs.getInt("prompt_tokens"));
        e.setCompletionTokens(rs.getInt("completion_tokens"));
        e.setTotalTokens(rs.getInt("total_tokens"));
        return e;
    };

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入一条消息 */
    public void save(MessageEntity entity) {
        jdbcTemplate.update(
            "INSERT INTO agent_message (id, session_id, role, name, content, metadata, " +
            "created_at, finished_at, prompt_tokens, completion_tokens, total_tokens) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            entity.getId(), entity.getSessionId(), entity.getRole(), entity.getName(),
            entity.getContentJson(), entity.getMetadataJson(),
            entity.getCreatedAt(), entity.getFinishedAt(),
            entity.getPromptTokens(), entity.getCompletionTokens(), entity.getTotalTokens()
        );
    }

    /** 查询会话最近 N 条消息(按时间正序) */
    public List<MessageEntity> findRecent(String sessionId, int limit) {
        String sql = "SELECT * FROM agent_message WHERE session_id = ? " +
                     "ORDER BY created_at DESC LIMIT ?";
        List<MessageEntity> desc = jdbcTemplate.query(sql, ROW_MAPPER, sessionId, limit);
        // 反转为正序
        java.util.Collections.reverse(desc);
        return desc;
    }

    /** 查询会话全部消息(按时间正序) */
    public List<MessageEntity> findAll(String sessionId) {
        String sql = "SELECT * FROM agent_message WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, sessionId);
    }

    /** 统计会话消息数 */
    public int count(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM agent_message WHERE session_id = ?",
            Integer.class, sessionId);
        return count != null ? count : 0;
    }

    /** 查询会话从第 offset 条开始的消息(用于取出旧消息压缩) */
    public List<MessageEntity> findOld(String sessionId, int keepRecent) {
        int total = count(sessionId);
        if (total <= keepRecent) return List.of();
        int offset = total - keepRecent;
        String sql = "SELECT * FROM (SELECT *, ROW_NUMBER() OVER (ORDER BY created_at) as rn " +
                     "FROM agent_message WHERE session_id = ?) t WHERE rn <= ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, sessionId, offset);
    }

    /** 删除会话全部消息 */
    public void deleteBySession(String sessionId) {
        jdbcTemplate.update("DELETE FROM agent_message WHERE session_id = ?", sessionId);
    }
}
