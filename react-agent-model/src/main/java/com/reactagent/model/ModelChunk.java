package com.reactagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式响应中的一个增量块。
 * 一个 chunk 可能包含文本增量、思考增量、或工具调用增量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelChunk {

    /** 文本增量(普通回复) */
    private String textDelta;

    /** 思考增量(推理链,部分模型支持) */
    private String thinkingDelta;

    /** 工具调用增量信息 */
    private String toolCallId;
    private String toolCallName;
    private Integer toolCallIndex;
    private String toolCallArgsDelta;

    /** 本次 chunk 是否是最终结束标记 */
    private boolean finished;

    /** token 用量(仅在最后一个 chunk 携带) */
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    /** 判断是否有文本内容 */
    public boolean hasText() {
        return textDelta != null && !textDelta.isEmpty();
    }

    /** 判断是否有思考内容 */
    public boolean hasThinking() {
        return thinkingDelta != null && !thinkingDelta.isEmpty();
    }

    /** 判断是否有工具调用增量 */
    public boolean hasToolCall() {
        return toolCallId != null || toolCallName != null
                || (toolCallArgsDelta != null && !toolCallArgsDelta.isEmpty());
    }
}
