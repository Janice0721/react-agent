package com.reactagent.skills;

import com.reactagent.core.skill.Skill;
import com.reactagent.core.skill.SkillMeta;
import com.reactagent.skills.impl.FileSkill;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册中心:管理所有技能的注册、查询、渐进式加载和组合。
 * <p>
 * 线程安全,Spring 单例 Bean。
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    /**
     * 已注册技能: name → Skill
     */
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    /**
     * 内置技能目录(classpath)
     */
    @Value("${agent.skill.base-dir:skills}")
    private String skillBaseDir;

    /**
     * 外部自定义技能目录(可选)
     */
    @Value("${agent.skill.external-dir:}")
    private String externalDir;

    @Autowired
    private SkillLoader loader;


    /**
     * 技能模块 Spring 配置。
     * <p>
     * 启动时:
     * 1. 从 classpath:skills/ 加载内置技能
     * 2. 从外部目录(可选)加载用户自定义技能
     * 3. 注册到 SkillRegistry
     * 4. 注册 LoadSkillTool 到 ToolKit
     */
    @PostConstruct
    public void init() {
        // 1. 加载内置技能(classpath:skills/)
        log.info("加载内置技能,目录: {}", skillBaseDir);
        List<FileSkill> builtins = loader.loadFromDirectory(skillBaseDir);
        builtins.forEach(this::register);

        // 2. 加载外部自定义技能(如果配置了)
        if (externalDir != null && !externalDir.isBlank()) {
            Path extPath = Paths.get(externalDir.replace("~",
                    System.getProperty("user.home")));
            if (Files.isDirectory(extPath)) {
                log.info("加载外部技能,目录: {}", extPath);
                List<FileSkill> customs = loader.loadFromDirectory(extPath.toString());
                customs.forEach(this::register);
            } else {
                log.warn("外部技能目录不存在: {}", extPath);
            }
        }

        log.info("技能注册完成,共 {} 个: {}", this.listNames().size(), this.listNames());
    }



    // ==================== 注册 / 注销 ====================

    /**
     * 注册一个技能
     */
    public void register(Skill skill) {
        if (skill == null || skill.name() == null) {
            log.warn("技能或名称为空,跳过注册");
            return;
        }
        skills.put(skill.name(), skill);
        log.info("注册技能: {} (L0 元数据已就绪)", skill.name());
    }


    /**
     * 注销技能
     */
    public Skill unregister(String name) {
        return skills.remove(name);
    }

    // ==================== 查询 ====================

    /**
     * 获取技能
     */
    public Skill get(String name) {
        return skills.get(name);
    }

    /**
     * 是否已注册
     */
    public boolean contains(String name) {
        return skills.containsKey(name);
    }

    /**
     * 获取所有已注册技能名
     */
    public List<String> listNames() {
        return new ArrayList<>(skills.keySet());
    }

    /**
     * L0: 获取所有技能的元数据列表。
     * 这些元数据会被注入 system prompt,让模型知道有哪些可用技能。
     */
    public List<SkillMeta> listMeta() {
        return skills.values().stream()
                .map(Skill::toMeta)
                .toList();
    }

    // ==================== 渐进式加载 ====================

    /**
     * L1+L2: 按需加载技能的完整指令和工具。
     * 模型调用 load_skill 工具时触发此方法。
     */
    public Skill load(String name) {
        Skill skill = skills.get(name);
        if (skill == null) {
            log.warn("技能未注册: {}", name);
            return null;
        }
        if (!skill.isInstructionsLoaded()) {
            skill.loadInstructions();
            log.info("按需加载技能指令: {}", name);
        }
        return skill;
    }

    /**
     * L2: 加载技能的专属工具。
     */
    public List<com.reactagent.core.tool.Tool> loadTools(String name) {
        Skill skill = skills.get(name);
        if (skill == null) {
            return List.of();
        }
        if (!skill.isToolsLoaded()) {
            skill.loadTools();
        }
        return skill.tools();
    }

    // ==================== 多技能组合 ====================

    /**
     * 组合多个技能,一次性加载。
     * 返回加载后的技能列表(指令已就绪)。
     */
    public List<Skill> compose(List<String> names) {
        List<Skill> loaded = new ArrayList<>();
        for (String name : names) {
            Skill skill = load(name);
            if (skill != null) {
                loaded.add(skill);
            }
        }
        return loaded;
    }
}
