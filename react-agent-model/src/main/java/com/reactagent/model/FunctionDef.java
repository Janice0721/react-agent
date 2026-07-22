package com.reactagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 函数定义:描述一个可被 LLM 调用的工具(对应 OpenAI tools.function)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionDef {
    /** 函数名称 */
    private String name;
    /** 函数描述(给 LLM 看) */
    private String description;
    /** 参数 JSON Schema */
    private JsonNode parameters;
}
