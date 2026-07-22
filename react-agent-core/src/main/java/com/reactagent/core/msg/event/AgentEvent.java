package com.reactagent.core.msg.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @ClassName: AgentEvent
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:12
 */
@Data
@AllArgsConstructor
public class AgentEvent {
    private String id;                    // 事件唯一 ID
    private String replyId;               // 关联的回复 ID
    private String sessionId;             // 会话 ID
    private EventType type;               // 事件类型
    private String blockId;               // 关联的内容块 ID
    private String toolCallId;            // 关联的工具调用 ID
    private Object payload;               // 载荷(文本增量/工具信息等)
    private String createdAt;
}
