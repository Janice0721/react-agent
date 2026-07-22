package com.reactagent.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {
    String name();
    String description();
    JsonNode schema();
    ToolResult invoke(JsonNode input, ToolContext ctx);
    boolean approvalRequired();
}