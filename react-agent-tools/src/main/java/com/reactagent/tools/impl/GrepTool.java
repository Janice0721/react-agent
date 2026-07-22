package com.reactagent.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Grep 工具:基于 ripgrep 搜索文件内容。
 * 若系统未安装 rg,自动回退到 grep。
 */
public class GrepTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() { return "grep"; }

    @Override
    public String description() {
        return "搜索文件内容(支持正则)。参数: pattern(必填,搜索模式,支持正则), "
             + "path(可选,搜索目录,默认当前工作目录), "
             + "include(可选,文件名过滤 glob,如 *.java), "
             + "caseSensitive(可选,是否大小写敏感,默认true)。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("pattern").put("type", "string").put("description", "搜索模式(支持正则)");
        props.putObject("path").put("type", "string").put("description", "搜索目录,默认工作目录");
        props.putObject("include").put("type", "string").put("description", "文件名 glob 过滤,如 *.java");
        props.putObject("caseSensitive").put("type", "boolean").put("description", "大小写敏感,默认true");
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

        String searchPath = input.has("path") ? input.get("path").asText() : ".";
        String include = input.has("include") ? input.get("include").asText() : null;
        boolean caseSensitive = input.has("caseSensitive")
                ? input.get("caseSensitive").asBoolean() : true;

        String workDir = ctx.getWorkDir() != null && !ctx.getWorkDir().isBlank()
                ? ctx.getWorkDir() : System.getProperty("user.dir");

        // 优先用 rg,回退 grep
        boolean hasRg = commandExists("rg");

        try {
            ProcessBuilder pb = hasRg ? buildRgCommand(pattern, searchPath, include, caseSensitive)
                                      : buildGrepCommand(pattern, searchPath, include, caseSensitive);
            pb.redirectErrorStream(true);
            pb.directory(new java.io.File(workDir));

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 500) {
                    output.append(line).append("\n");
                    lineCount++;
                }
            }

            process.waitFor(30, TimeUnit.SECONDS);
            process.destroyForcibly();

            String result = output.toString().trim();
            if (result.isEmpty()) {
                return ToolResult.success(null, "未找到匹配");
            }
            return ToolResult.success(null, result);
        } catch (Exception e) {
            return ToolResult.error(null, "搜索失败: " + e.getMessage());
        }
    }

    private ProcessBuilder buildRgCommand(String pattern, String path, String include,
                                           boolean caseSensitive) {
        ProcessBuilder pb = new ProcessBuilder("rg", "--line-number", "--no-heading");
        if (!caseSensitive) pb.command().add("-i");
        if (include != null) {
            pb.command().add("-g");
            pb.command().add(include);
        }
        // 安全检查 pattern
        pb.command().add(pattern);
        pb.command().add(path);
        return pb;
    }

    private ProcessBuilder buildGrepCommand(String pattern, String path, String include,
                                            boolean caseSensitive) {
        ProcessBuilder pb = new ProcessBuilder("grep", "-rn", "-E");
        if (!caseSensitive) pb.command().add("-i");
        pb.command().add("--include=" + (include != null ? include : "*"));
        pb.command().add(pattern);
        pb.command().add(path);
        return pb;
    }

    private boolean commandExists(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            p.waitFor(5, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
