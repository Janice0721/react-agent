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
 * 文件编辑工具:精确字符串替换。默认需审批。
 */
public class EditTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() { return "edit"; }

    @Override
    public String description() {
        return "编辑文件:将文件中的 oldString 替换为 newString。"
             + "参数: path(必填), oldString(必填,要替换的原文,必须唯一), "
             + "newString(必填,替换后的内容)。"
             + "若 oldString 在文件中出现多次会报错,请提供更多上下文使其唯一。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("path").put("type", "string").put("description", "文件路径");
        props.putObject("oldString").put("type", "string").put("description", "要替换的原文");
        props.putObject("newString").put("type", "string").put("description", "替换后的内容");
        schema.putArray("required").add("path").add("oldString").add("newString");
        return schema;
    }

    @Override
    public boolean approvalRequired() { return true; }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String pathStr = input.path("path").asText("");
        String oldStr = input.path("oldString").asText("");
        String newStr = input.path("newString").asText("");

        if (pathStr.isBlank() || oldStr.isBlank()) {
            return ToolResult.error(null, "参数 path 和 oldString 不能为空");
        }

        try {
            Path path = resolvePath(pathStr, ctx);
            if (!Files.exists(path)) {
                return ToolResult.error(null, "文件不存在: " + path);
            }

            String content = Files.readString(path, Charset.defaultCharset());

            // 检查匹配次数:必须唯一匹配
            int count = countOccurrences(content, oldStr);
            if (count == 0) {
                return ToolResult.error(null, "未找到匹配文本,请检查 oldString 是否正确");
            }
            if (count > 1) {
                return ToolResult.error(null, "oldString 匹配到 " + count + " 处,请提供更多上下文使其唯一");
            }

            // 执行替换
            String newContent = content.replace(oldStr, newStr);
            Files.writeString(path, newContent, Charset.defaultCharset());

            return ToolResult.success(null, "已替换并写入 " + path);
        } catch (Exception e) {
            return ToolResult.error(null, "编辑失败: " + e.getMessage());
        }
    }

    /** 统计子串出现次数 */
    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    protected Path resolvePath(String pathStr, ToolContext ctx) {
        Path path = Path.of(pathStr);
        if (path.isAbsolute()) return path;
        String workDir = ctx.getWorkDir() != null ? ctx.getWorkDir() : System.getProperty("user.dir");
        return Path.of(workDir).resolve(pathStr).normalize();
    }
}
