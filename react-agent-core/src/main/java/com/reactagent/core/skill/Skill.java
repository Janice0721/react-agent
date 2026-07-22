package com.reactagent.core.skill;

import com.reactagent.core.tool.Tool;

import java.util.List;
import java.util.Map;

/**
 * Skill:可加载的能力包,支持渐进式披露。
 * <p>
 * 三层加载:
 * <ul>
 *   <li>L0 元数据(name/description/whenToUse) — 始终加载,注入 system prompt</li>
 *   <li>L1 指令(instructions) — 模型调用 load_skill 时按需加载</li>
 *   <li>L2 工具(tools) — 实际执行时才加载</li>
 * </ul>
 */
public interface Skill {

    /** 技能名称(唯一标识) */
    String name();

    /** 简短描述(给模型看,告诉它这个技能做什么) */
    String description();

    /** 何时使用此技能(给模型看,帮助判断是否需要加载) */
    String whenToUse();

    /**
     * 完整指令文档(L1),模型加载技能后注入上下文。
     * 调用前应先检查 isInstructionsLoaded()。
     */
    String instructions();

    /** 标记指令是否已加载 */
    boolean isInstructionsLoaded();

    /** 加载 L1 指令 */
    void loadInstructions();

    /**
     * 专属工具列表(L2),执行时才加载。
     * 可能为空(纯指令型技能没有专属工具)。
     */
    List<Tool> tools();

    /** 标记工具是否已加载 */
    boolean isToolsLoaded();

    /** 加载 L2 工具 */
    void loadTools();

    /** 获取 L0 元数据快照 */
    default SkillMeta toMeta() {
        return new SkillMeta(name(), description(), whenToUse());
    }
}
