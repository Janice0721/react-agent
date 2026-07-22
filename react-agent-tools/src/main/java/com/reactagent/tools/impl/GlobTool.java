package com.reactagent.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Glob 工具:按文件名模式匹配查找文件。
 */
public class GlobTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_RESULTS = 500;

    @Override
    public String name() { return "glob"; }

    @Override
    public String description() {
        return "按文件名模式查找文件。参数: pattern(必填,glob 模式如 **/*.java), "
             + "path(可选,搜索根目录,默认工作目录)。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("pattern").put("type", "string").put("description", "glob 模式,如 **/*.java");
        props.putObject("path").put("type", "string").put("description", "搜索根目录,默认工作目录");
        schema.putArray("required").add("pattern");
        return schema;
    }

    @Override
    public boolean approvalRequired() { return false; }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String pattern = input.path("pattern").asText("");
        if (pattern.isBlank()) {
            return ToolResult.error(null, "参数 pattern 不能为空");
        }

        String rootPath = input.has("path") ? input.get("path").asText()
                : (ctx.getWorkDir() != null ? ctx.getWorkDir() : ".");

        Path root = Paths.get(rootPath);
        if (!root.isAbsolute()) {
            String workDir = ctx.getWorkDir() != null && !ctx.getWorkDir().isBlank()
                    ? ctx.getWorkDir() : System.getProperty("user.dir");
            root = Paths.get(workDir).resolve(rootPath).normalize();
        }

        if (!Files.exists(root)) {
            return ToolResult.error(null, "路径不存在: " + root);
        }

        final Path searchRoot = root;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> results = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .forEach(p -> {
                      if (results.size() >= MAX_RESULTS) return;
                      Path relative = searchRoot.relativize(p);
                      if (matcher.matches(relative) || matcher.matches(p)) {
                          results.add(p.toString());
                      }
                  });
        } catch (Exception e) {
            return ToolResult.error(null, "搜索失败: " + e.getMessage());
        }

        if (results.isEmpty()) {
            return ToolResult.success(null, "未找到匹配文件");
        }
        StringBuilder sb = new StringBuilder();
        for (String r : results) {
            sb.append(r).append("\n");
        }
        sb.append("\n--- 共 ").append(results.size()).append(results.size() >= MAX_RESULTS ? "+ " : " ")
          .append("个文件 ---");
        return ToolResult.success(null, sb.toString().trim());
    }
}
