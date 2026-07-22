package com.reactagent.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件写入工具(覆盖写入)。默认需审批。
 */
public class WriteTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() { return "write"; }

    @Override
    public String description() {
        return "写入文件(覆盖原有内容)。参数: path(必填,文件路径), content(必填,要写入的内容)。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "文件路径");
        props.putObject("content").put("type", "string").put("description", "要写入的内容");
        schema.putArray("required").add("path").add("content");
        return schema;
    }

    @Override
    public boolean approvalRequired() { return true; }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String pathStr = input.path("path").asText("");
        String content = input.path("content").asText("");
        if (pathStr.isBlank()) {
            return ToolResult.error(null, "参数 path 不能为空");
        }

        try {
            Path path = resolvePath(pathStr, ctx);
            // 确保父目录存在
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content, Charset.defaultCharset());
            return ToolResult.success(null, "已写入 " + path + " (" + content.length() + " 字符)");
        } catch (Exception e) {
            return ToolResult.error(null, "写入失败: " + e.getMessage());
        }
    }

    protected Path resolvePath(String pathStr, ToolContext ctx) {
        Path path = Path.of(pathStr);
        if (path.isAbsolute()) {
            return path;
        }
        String workDir = ctx.getWorkDir() != null ? ctx.getWorkDir() : System.getProperty("user.dir");
        return Path.of(workDir).resolve(pathStr).normalize();
    }
}
