package com.reactagent.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 脚本执行工具:运行 Python 或 Node.js 脚本。默认需审批。
 * 支持两种模式:
 * 1. 直接传 code 内联执行
 * 2. 传 scriptPath 执行文件
 */
public class ScriptTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ScriptTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() { return "script"; }

    @Override
    public String description() {
        return "运行 Python 或 Node.js 脚本。参数: language(必填,python 或 node), "
             + "code(可选,内联代码), scriptPath(可选,脚本文件路径), "
             + "timeout(可选,超时秒数,默认30)。code 和 scriptPath 二选一。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("language").put("type", "string")
                .put("description", "脚本语言: python 或 node");
        props.putObject("code").put("type", "string").put("description", "内联代码");
        props.putObject("scriptPath").put("type", "string").put("description", "脚本文件路径");
        props.putObject("timeout").put("type", "integer").put("description", "超时秒数");
        schema.putArray("required").add("language");
        return schema;
    }

    @Override
    public boolean approvalRequired() { return true; }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String language = input.path("language").asText("").toLowerCase();
        String code = input.has("code") ? input.get("code").asText() : null;
        String scriptPath = input.has("scriptPath") ? input.get("scriptPath").asText() : null;
        int timeout = input.has("timeout") ? input.get("timeout").asInt(30) : 30;
        if (ctx.getTimeoutSeconds() > 0) {
            timeout = ctx.getTimeoutSeconds();
        }

        if (!language.equals("python") && !language.equals("node")) {
            return ToolResult.error(null, "language 只支持 python 或 node");
        }
        if ((code == null || code.isBlank()) && (scriptPath == null || scriptPath.isBlank())) {
            return ToolResult.error(null, "必须提供 code 或 scriptPath");
        }

        try {
            ProcessBuilder pb;
            Path tempFile = null;

            if (code != null && !code.isBlank()) {
                // 内联模式:写临时文件执行
                String ext = language.equals("python") ? ".py" : ".js";
                tempFile = Files.createTempFile("react-agent-script-", ext);
                Files.writeString(tempFile, code, Charset.defaultCharset());

                if (language.equals("python")) {
                    pb = new ProcessBuilder("python3", tempFile.toString());
                } else {
                    pb = new ProcessBuilder("node", tempFile.toString());
                }
            } else {
                // 文件模式
                if (language.equals("python")) {
                    pb = new ProcessBuilder("python3", scriptPath);
                } else {
                    pb = new ProcessBuilder("node", scriptPath);
                }
            }

            pb.redirectErrorStream(true);
            if (ctx.getWorkDir() != null && !ctx.getWorkDir().isBlank()) {
                pb.directory(new File(ctx.getWorkDir()));
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            // 清理临时文件
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }

            if (!finished) {
                process.destroyForcibly();
                return ToolResult.error(null, "脚本执行超时(" + timeout + "s)");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (exitCode == 0) {
                return ToolResult.success(null, result.isEmpty() ? "(无输出)" : result);
            } else {
                return ToolResult.error(null, "退出码: " + exitCode + "\n" + result);
            }
        } catch (Exception e) {
            log.error("脚本执行失败", e);
            return ToolResult.error(null, "执行异常: " + e.getMessage());
        }
    }
}
