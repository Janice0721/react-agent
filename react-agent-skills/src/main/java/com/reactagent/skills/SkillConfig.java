package com.reactagent.skills;

import com.reactagent.skills.impl.FileSkill;
import com.reactagent.tools.ToolKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 技能模块 Spring 配置。
 * 启动时:
 * 1. 从 classpath:skills/ 加载内置技能
 * 2. 从外部目录(可选)加载用户自定义技能
 * 3. 注册到 SkillRegistry
 * 4. 注册 LoadSkillTool 到 ToolKit
 */
@Configuration
public class SkillConfig {

    private static final Logger log = LoggerFactory.getLogger(SkillConfig.class);

    @Value("${agent.skill.base-dir:skills}")
    private String skillBaseDir;

    @Value("${agent.skill.external-dir:}")
    private String externalDir;

    @Autowired
    private ToolKit toolKit;

    @Bean
    public SkillRegistry skillRegistry() {
        SkillRegistry registry = new SkillRegistry();
        SkillLoader loader = new SkillLoader();

        log.info("加载内置技能,目录: {}", skillBaseDir);
        List<FileSkill> builtins = loader.loadFromDirectory(skillBaseDir);
        builtins.forEach(registry::register);

        if (externalDir != null && !externalDir.isBlank()) {
            Path extPath = Paths.get(externalDir.replace("~",
                    System.getProperty("user.home")));
            if (Files.isDirectory(extPath)) {
                log.info("加载外部技能,目录: {}", extPath);
                List<FileSkill> customs = loader.loadFromDirectory(extPath.toString());
                customs.forEach(registry::register);
            }
        }

        log.info("技能注册完成,共 {} 个: {}", registry.listNames().size(), registry.listNames());

        com.reactagent.core.tool.Tool tool = new LoadSkillTool(registry);
        toolKit.register(tool);
        log.info("LoadSkillTool 已注册到 ToolKit");
        return registry;
    }
}
