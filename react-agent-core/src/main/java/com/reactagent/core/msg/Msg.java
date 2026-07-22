package com.reactagent.core.msg;

import com.reactagent.core.msg.block.ContentBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.core.msg.block.ThinkingBlock;
import com.reactagent.core.msg.block.ToolCallBlock;
import com.reactagent.core.msg.block.ToolResultBlock;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 消息:对话的一轮,持久化和上下文管理的基本单位。
 */
@Data
public class Msg {
    private String id;                    // 唯一 ID (UUID)
    private String sessionId;             // 所属会话
    private String name;                  // 发送者名称
    private Role role;                    // user / assistant / system
    private List<ContentBlock> content;   // 内容块列表
    private Map<String, Object> metadata; // 任意元数据
    private String createdAt;             // 创建时间 ISO 8601
    private String finishedAt;            // 完成时间
    private Usage usage;                  // token 用量

    // ===== 工厂方法 =====

    public static Msg user(String sessionId, String name, String text) {
        Msg msg = new Msg();
        msg.setId(UUID.randomUUID().toString());
        msg.setSessionId(sessionId);
        msg.setName(name);
        msg.setRole(Role.USER);
        msg.setContent(new ArrayList<>(List.of(new TextBlock(text))));
        msg.setCreatedAt(Instant.now().toString());
        return msg;
    }

    public static Msg user(String sessionId, String name, List<ContentBlock> content) {
        Msg msg = new Msg();
        msg.setId(UUID.randomUUID().toString());
        msg.setSessionId(sessionId);
        msg.setName(name);
        msg.setRole(Role.USER);
        msg.setContent(new ArrayList<>(content));
        msg.setCreatedAt(Instant.now().toString());
        return msg;
    }

    public static Msg assistant(String sessionId, String name, List<ContentBlock> content) {
        Msg msg = new Msg();
        msg.setId(UUID.randomUUID().toString());
        msg.setSessionId(sessionId);
        msg.setName(name);
        msg.setRole(Role.ASSISTANT);
        msg.setContent(new ArrayList<>(content));
        msg.setCreatedAt(Instant.now().toString());
        return msg;
    }

    public static Msg system(String sessionId, String text) {
        Msg msg = new Msg();
        msg.setId(UUID.randomUUID().toString());
        msg.setSessionId(sessionId);
        msg.setName("system");
        msg.setRole(Role.SYSTEM);
        msg.setContent(new ArrayList<>(List.of(new TextBlock(text))));
        msg.setCreatedAt(Instant.now().toString());
        return msg;
    }

    /**
     * 构造工具结果消息(OpenAI 格式中 role 为 user/tool)
     */
    public static Msg toolResult(String sessionId, String toolCallId,
                                  String toolName, String output, boolean success) {
        ToolResultBlock block = new ToolResultBlock();
        block.setId(toolCallId);
        block.setName(toolName);
        block.setOutput(output);
        block.setState(success
                ? com.reactagent.core.msg.block.ToolResultState.SUCCESS
                : com.reactagent.core.msg.block.ToolResultState.ERROR);

        Msg msg = new Msg();
        msg.setId(UUID.randomUUID().toString());
        msg.setSessionId(sessionId);
        msg.setName("tool");
        msg.setRole(Role.USER);
        msg.setContent(new ArrayList<>(List.of(block)));
        msg.setCreatedAt(Instant.now().toString());
        return msg;
    }

    // ===== 辅助方法 =====

    /** 获取所有文本内容拼接 */
    public String getTextContent() {
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    /** 获取所有工具调用 */
    public List<ToolCallBlock> getToolCalls() {
        List<ToolCallBlock> calls = new ArrayList<>();
        if (content == null) return calls;
        for (ContentBlock block : content) {
            if (block instanceof ToolCallBlock tc) {
                calls.add(tc);
            }
        }
        return calls;
    }

    public boolean hasToolCalls() {
        if (content == null) return false;
        return content.stream().anyMatch(b -> b instanceof ToolCallBlock);
    }

    /** 获取思考内容 */
    public String getThinkingContent() {
        if (content == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof ThinkingBlock tb) {
                sb.append(tb.getThinking());
            }
        }
        return sb.toString();
    }
}
