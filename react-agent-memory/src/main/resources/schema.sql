-- 记忆模块建表脚本
-- 数据库: react_agent

-- 消息持久化表(短期记忆落库)
CREATE TABLE IF NOT EXISTS agent_message (
    id              VARCHAR(64) PRIMARY KEY COMMENT '消息唯一ID',
    session_id      VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    role            VARCHAR(16) NOT NULL COMMENT '角色: USER/ASSISTANT/SYSTEM',
    name            VARCHAR(64) COMMENT '发送者名称',
    content         JSON COMMENT '内容块列表JSON',
    metadata        JSON COMMENT '元数据JSON',
    created_at      VARCHAR(32) NOT NULL COMMENT '创建时间ISO8601',
    finished_at     VARCHAR(32) COMMENT '完成时间',
    prompt_tokens   INT DEFAULT 0 COMMENT '输入token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出token数',
    total_tokens    INT DEFAULT 0 COMMENT '总token数',
    INDEX idx_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息持久化表';

-- 会话表
CREATE TABLE IF NOT EXISTS agent_session (
    id          VARCHAR(64) PRIMARY KEY COMMENT '会话ID',
    user_id     VARCHAR(64) COMMENT '用户ID',
    title       VARCHAR(256) COMMENT '会话标题',
    status      VARCHAR(32) DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUSPENDED/ABORTED/ENDED',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';
