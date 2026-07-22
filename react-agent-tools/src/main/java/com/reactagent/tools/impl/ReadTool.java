package com.reactagent.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件读取工具。支持指定行范围。
 */
public class ReadTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_LINES = 2000;

    @Override
    public String name() { return "read"; }

    @Override
    public String description() {
        return "读取文件内容。参数: path(必填,文件路径), offset(可选,起始行,从1开始), "
             + "limit(可选,读取行数,默认读取全部,最多" + MAX_LINES + "行)。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "要读取的文件路径");
        props.putObject("offset").put("type", "integer").put("description", "起始行号(从1开始)");
        props.putObject("limit").put("type", "integer").put("description", "读取行数");
        schema.putArray("required").add("path");
        return schema;
    }

    @Override
    public boolean approvalRequired() { return false; }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String pathStr = input.path("path").asText("");
        if (pathStr.isBlank()) {
            return ToolResult.error(null, "参数 path 不能为空");
        }

        int offset = input.has("offset") ? input.get("offset").asInt(1) : 1;
        int limit = input.has("limit") ? input.get("limit").asInt(MAX_LINES) : MAX_LINES;

        Path path = resolvePath(pathStr, ctx);
        File file = path.toFile();

        if (!file.exists()) {
            return ToolResult.error(null, "文件不存在: " + path);
        }
        if (file.isDirectory()) {
            return ToolResult.error(null, "路径是目录不是文件: " + path);
        }

        try {
            List<String> lines = Files.readAllLines(path, Charset.defaultCharset());
            int start = Math.max(0, offset - 1);
            int end = Math.min(lines.size(), start + limit);

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(i + 1).append("\t").append(lines.get(i)).append("\n");
            }

            if (sb.isEmpty()) {
                return ToolResult.success(null, "(文件为空或超出范围)");
            }
            // 末尾附加行数信息
            sb.append("\n--- 共 ").append(lines.size()).append(" 行,显示 ").append(end - start).append(" 行 ---");
            return ToolResult.success(null, sb.toString());
        } catch (Exception e) {
            return ToolResult.error(null, "读取失败: " + e.getMessage());
        }
    }

    /** 解析相对路径(基于工作目录) */
    protected Path resolvePath(String pathStr, ToolContext ctx) {
        Path path = Path.of(pathStr);
        if (path.isAbsolute()) {
            return path;
        }
        String workDir = ctx.getWorkDir() != null ? ctx.getWorkDir() : System.getProperty("user.dir");
        return Path.of(workDir).resolve(pathStr).normalize();
    }
}
