package com.reactagent.core.tool;

import lombok.Data;

import java.util.Map;

/**
 * 工具执行结果。
 */
@Data
public class ToolResult {

    /** 工具调用 ID(对应 ToolCallBlock.id) */
    private String id;
    /** 是否执行成功 */
    private boolean success;
    /** 输出内容 */
    private String output;
    /** 额外元数据 */
    private Map<String, Object> metadata;

    public static ToolResult success(String id, String output) {
        ToolResult r = new ToolResult();
        r.setId(id);
        r.setSuccess(true);
        r.setOutput(output);
        return r;
    }

    public static ToolResult error(String id, String error) {
        ToolResult r = new ToolResult();
        r.setId(id);
        r.setSuccess(false);
        r.setOutput(error);
        return r;
    }
}
