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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Bash 工具:在终端执行 shell 命令。
 * 默认需要用户审批。
 */
public class BashTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 命令黑名单:禁止执行的危险命令模式 */
    private static final Pattern[] BLOCKED_PATTERNS = {
            Pattern.compile("\\brm\\s+-rf\\s+/"),
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile("\\bdd\\s+if="),
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};"),  // fork bomb
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
    };

    @Override
    public String name() { return "bash"; }

    @Override
    public String description() {
        return "在终端执行 shell 命令。参数: command(必填,要执行的命令)。"
             + "可设置 timeout(可选,超时秒数,默认30)。";
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("command").put("type", "string").put("description", "要执行的 shell 命令");
        props.putObject("timeout").put("type", "integer").put("description", "超时秒数,默认30");
        schema.putArray("required").add("command");
        return schema;
    }

    @Override
    public boolean approvalRequired() { return true; }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String command = input.path("command").asText("");
        if (command.isBlank()) {
            return ToolResult.error(null, "参数 command 不能为空");
        }

        // 安全检查:命令黑名单
        for (Pattern p : BLOCKED_PATTERNS) {
            if (p.matcher(command).find()) {
                return ToolResult.error(null, "命令被安全策略拦截: " + command);
            }
        }

        int timeout = input.has("timeout") ? input.get("timeout").asInt(30) : 30;
        if (ctx.getTimeoutSeconds() > 0) {
            timeout = ctx.getTimeoutSeconds();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            if (ctx.getWorkDir() != null && !ctx.getWorkDir().isBlank()) {
                pb.directory(new File(ctx.getWorkDir()));
            }
            if (ctx.getEnv() != null) {
                pb.environment().putAll(ctx.getEnv());
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
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.error(null, "命令执行超时(" + timeout + "s): " + command);
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (exitCode == 0) {
                return ToolResult.success(null, result.isEmpty() ? "(无输出)" : result);
            } else {
                return ToolResult.error(null, "退出码: " + exitCode + "\n输出: " + result);
            }
        } catch (Exception e) {
            log.error("Bash 执行失败", e);
            return ToolResult.error(null, "执行异常: " + e.getMessage());
        }
    }
}
