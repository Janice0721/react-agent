package com.reactagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactagent.core.msg.block.ToolCallBlock;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;
import com.reactagent.model.FunctionDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具箱:管理所有工具的注册、查询、审批标记、执行。
 * <p>
 * 线程安全,可作为 Spring Bean 单例使用。
 */
public class ToolKit {

    private static final Logger log = LoggerFactory.getLogger(ToolKit.class);

    /** 已注册工具: name → Tool */
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /** 需要用户审批才能执行的工具名 */
    private final Set<String> approvalRequired = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 注册 / 注销 ====================

    /**
     * 注册一个工具。
     * 若工具自身声明 approvalRequired=true,自动加入审批清单。
     */
    public void register(Tool tool) {
        tools.put(tool.name(), tool);
        if (tool.approvalRequired()) {
            approvalRequired.add(tool.name());
        }
        log.info("注册工具: {} (需审批: {})", tool.name(), tool.approvalRequired());
    }

    /** 批量注册 */
    public void registerAll(List<Tool> toolList) {
        toolList.forEach(this::register);
    }

    /** 注销工具 */
    public Tool unregister(String name) {
        approvalRequired.remove(name);
        Tool removed = tools.remove(name);
        if (removed != null) {
            log.info("注销工具: {}", name);
        }
        return removed;
    }

    /** 标记某工具需要审批 */
    public void markApprovalRequired(String toolName) {
        approvalRequired.add(toolName);
    }

    /** 取消某工具的审批要求 */
    public void unmarkApprovalRequired(String toolName) {
        approvalRequired.remove(toolName);
    }

    // ==================== 查询 ====================

    /** 获取工具 */
    public Tool get(String name) {
        return tools.get(name);
    }

    /** 是否已注册 */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /** 该工具是否需要审批 */
    public boolean needsApproval(String toolName) {
        return approvalRequired.contains(toolName);
    }

    /** 获取所有已注册工具名 */
    public Set<String> listNames() {
        return tools.keySet();
    }

    /** 获取所有工具 */
    public List<Tool> listAll() {
        return new ArrayList<>(tools.values());
    }

    // ==================== 转换为 Function Calling ====================

    /**
     * 将所有工具转换为 OpenAI Function Calling 格式,
     * 供模型调用时传入 tools 参数。
     */
    public List<FunctionDef> toFunctionSpecs() {
        return tools.values().stream()
                .map(t -> FunctionDef.builder()
                        .name(t.name())
                        .description(t.description())
                        .parameters(t.schema())
                        .build())
                .toList();
    }

    // ==================== 执行 ====================

    /**
     * 执行一次工具调用。
     *
     * @param call    模型返回的工具调用块
     * @param ctx     执行上下文(工作目录/超时/环境变量)
     * @return 执行结果,失败时 success=false 而非抛异常
     */
    public ToolResult invoke(ToolCallBlock call, ToolContext ctx) {
        String toolName = call.getName();
        String callId = call.getId();

        // 1. 查找工具
        Tool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("工具未注册: {}", toolName);
            return ToolResult.error(callId, "工具未注册: " + toolName);
        }

        // 2. 解析参数
        JsonNode input;
        try {
            input = parseJson(call.getInput());
        } catch (Exception e) {
            log.error("工具参数解析失败: {} input={}", toolName, call.getInput(), e);
            return ToolResult.error(callId, "参数解析失败: " + e.getMessage());
        }

        // 3. 执行(捕获所有异常,避免打断 ReAct 循环)
        try {
            log.info("执行工具: {} callId={}", toolName, callId);
            ToolResult result = tool.invoke(input, ctx);
            if (result == null) {
                result = ToolResult.error(callId, "工具返回 null");
            }
            // 补全 id
            if (result.getId() == null) {
                result.setId(callId);
            }
            log.info("工具执行完成: {} success={}", toolName, result.isSuccess());
            return result;
        } catch (Exception e) {
            log.error("工具执行异常: {}", toolName, e);
            return ToolResult.error(callId, "执行异常: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    private JsonNode parseJson(String input) throws Exception {
        if (input == null || input.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(input);
    }
}
