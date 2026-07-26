package com.reactagent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.skill.Skill;
import com.reactagent.core.tool.Tool;
import com.reactagent.core.tool.ToolContext;
import com.reactagent.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * load_skill 工具:模型调用此工具来按需加载一个技能的完整指令。
 * <p>
 * 这是渐进式披露的关键:
 * - system prompt 中注入技能 L0 概览(名称+描述+何时使用)
 * - 模型判断需要某技能时,调用 load_skill(name)
 * - 引擎返回该技能的 L1 完整指令,注入上下文
 * - 模型按指令执行任务
 */
public class LoadSkillTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SkillRegistry registry;

    public LoadSkillTool(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "load_skill";
    }

    @Override
    public String description() {
        StringBuilder sb = new StringBuilder();
        sb.append("按需加载一个技能的完整指令。");
        sb.append("当任务需要某个专业技能时,先调用此工具加载指令,再按指令执行。");
        sb.append("可用技能:\n");
        registry.listMeta().forEach(meta -> {
            sb.append("  - ").append(meta.getName())
              .append(": ").append(meta.getDescription()).append("\n");
        });
        return sb.toString();
    }

    @Override
    public JsonNode schema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("name")
                .put("type", "string")
                .put("description", "要加载的技能名称");
        schema.putArray("required").add("name");
        return schema;
    }

    @Override
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String skillName = input.path("name").asText("");
        if (skillName.isBlank()) {
            return ToolResult.error(null, "参数 name 不能为空");
        }

        Skill skill = registry.load(skillName);
        if (skill == null) {
            return ToolResult.error(null,
                    "技能未注册: " + skillName + "。可用技能: "
                    + String.join(", ", registry.listNames()));
        }

        log.info("模型请求加载技能: {}", skillName);

        StringBuilder result = new StringBuilder();
        result.append("=== 技能已加载: ").append(skill.name()).append(" ===\n");
        result.append("描述: ").append(skill.description()).append("\n");
        result.append("使用场景: ").append(skill.whenToUse()).append("\n");
        result.append("\n--- 技能指令 ---\n");
        result.append(skill.instructions());

        if (!skill.tools().isEmpty()) {
            result.append("\n--- 技能专属工具 ---\n");
            for (Tool t : skill.tools()) {
                result.append("- ").append(t.name()).append(": ").append(t.description()).append("\n");
            }
        }

        return ToolResult.success(null, result.toString());
    }

    @Override
    public boolean approvalRequired() {
        return false;
    }
}
