package com.reactagent.core.msg.event;

/**
 * @ClassName: EventType
 * @Description: todo
 * @Author XuZheng
 * @Date 2026/7/22 11:13
 */
public enum EventType {
    // 生命周期
    REPLY_START, REPLY_END, EXCEED_MAX_ITERS,
    // 文本流
    TEXT_BLOCK_START, TEXT_BLOCK_DELTA, TEXT_BLOCK_END,
    // 思考流
    THINKING_BLOCK_START, THINKING_BLOCK_DELTA, THINKING_BLOCK_END,
    // 工具流
    TOOL_CALL_START, TOOL_CALL_DELTA, TOOL_CALL_END,
    TOOL_RESULT_START, TOOL_RESULT_END,
    // 人机协同
    HITL_PERMISSION_REQUEST, HITL_USER_INPUT_REQUEST, HITL_RESUMED,
    // 上下文治理
    CONTEXT_COMPRESSED,
    // 会话控制
    SESSION_PAUSED, SESSION_RESUMED, SESSION_ABORTED,
    // 异常
    ERROR
}