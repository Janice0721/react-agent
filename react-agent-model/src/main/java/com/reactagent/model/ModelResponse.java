package com.reactagent.model;

import com.reactagent.core.msg.Usage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 非流式调用的完整响应。
 */
@Data
public class ModelResponse {

    /** 回复文本内容 */
    private String text;

    /** 思考内容(部分模型支持) */
    private String thinking;

    /** 工具调用列表 */
    private List<ToolCallInfo> toolCalls;

    /** token 用量 */
    private Usage usage;

    /** 结束原因: stop / tool_calls / length */
    private String finishReason;

    public ModelResponse() {
        this.toolCalls = new ArrayList<>();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 工具调用信息
     */
    @Data
    public static class ToolCallInfo {
        /** 工具调用唯一 ID */
        private String id;
        /** 工具/函数名称 */
        private String name;
        /** 参数 JSON 字符串 */
        private String arguments;
        /** 调用索引(流式场景用于区分多个工具) */
        private Integer index;

        public ToolCallInfo() {}

        public ToolCallInfo(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }
}
