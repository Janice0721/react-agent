package com.reactagent.skills.impl;

import com.reactagent.core.skill.Skill;
import com.reactagent.core.skill.SkillMeta;
import com.reactagent.core.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 基于文件的 Skill 实现。
 * <p>
 * 从 SKILL.md 文件加载,支持 front matter 元数据解析。
 * 支持渐进式披露:L0 元数据始终可用,L1 指令按需加载,L2 工具延迟实例化。
 * <p>
 * L0元数据每次加载到prompt中
 * L1指令按需加载,在调用时才加载到prompt中
 * L2工具延迟实例化,在调用时才实例化，例如脚本，工具等，对于脚本执行通过工具加载即可
 */
public class FileSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(FileSkill.class);

    /**
     * 技能目录路径
     */
    private final Path skillDir;

    /**
     * SKILL.md 文件路径
     */
    private final Path skillFile;

    // L0: 元数据(始终加载)
    private String name;
    private String description;
    private String whenToUse;

    // L1: 指令(按需加载)
    private String instructions;
    private boolean instructionsLoaded = false;

    // L2: 工具(执行时加载)
    private List<Tool> tools = Collections.emptyList();
    private boolean toolsLoaded = false;

    public FileSkill(Path skillDir, Path skillFile) {
        this.skillDir = skillDir;
        this.skillFile = skillFile;
    }

    /**
     * 从 SKILL.md 解析 front matter + 正文。
     * Front matter 格式:
     * <pre>
     * ---
     * name: code-review
     * description: 代码审查技能
     * whenToUse: 当用户要求审查代码时
     * ---
     * 正文内容...
     * </pre>
     */
    public void parse() {
        try {
            String content = Files.readString(skillFile, Charset.defaultCharset());
            String[] parts = parseFrontMatter(content);

            // parts[0] = front matter 文本, parts[1] = 正文(延迟加载,先不存)
            String frontMatter = parts[0];
            this.instructions = parts[1];
            this.instructionsLoaded = false;  // 正文已读但标记为未"加载"(按需注入时才置 true)

            // 解析 front matter 的 key: value
            for (String line : frontMatter.split("\n")) {
                line = line.trim();
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                switch (key) {
                    case "name" -> this.name = val;
                    case "description" -> this.description = val;
                    case "whenToUse" -> this.whenToUse = val;
                    default -> {
                    }  // 忽略未知字段
                }
            }

            if (name == null || name.isBlank()) {
                // 用目录名作为 name
                name = skillDir.getFileName().toString();
            }
            if (description == null) description = "";
            if (whenToUse == null) whenToUse = "";

            log.info("解析技能文件: {} → name={}, dir={}", skillFile, name, skillDir);
        } catch (Exception e) {
            log.error("解析技能文件失败: {}", skillFile, e);
            this.name = skillDir.getFileName().toString();
            this.description = "(解析失败)";
            this.whenToUse = "";
            this.instructions = "(技能文件解析失败: " + e.getMessage() + ")";
        }
    }

    /**
     * 分离 front matter 和正文。
     * 返回 String[2]: [0]=frontMatter, [1]=body
     */
    private String[] parseFrontMatter(String content) {
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("---")) {
            return new String[]{"", content};
        }
        // 找第二个 ---
        int firstEnd = trimmed.indexOf('\n');
        if (firstEnd < 0) return new String[]{"", content};

        int secondStart = trimmed.indexOf("\n---", firstEnd);
        if (secondStart < 0) return new String[]{"", content};

        String frontMatter = trimmed.substring(firstEnd + 1, secondStart).trim();
        String body = trimmed.substring(secondStart + 4).stripLeading();  // 跳过 "\n---"
        return new String[]{frontMatter, body};
    }

    // ===== Skill 接口实现 =====

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String whenToUse() {
        return whenToUse;
    }

    @Override
    public String instructions() {
        if (!instructionsLoaded) {
            loadInstructions();
        }
        return instructions;
    }

    @Override
    public boolean isInstructionsLoaded() {
        return instructionsLoaded;
    }

    @Override
    public void loadInstructions() {
        // 文件已在 parse() 时读取,这里只做标记
        instructionsLoaded = true;
        log.info("加载技能指令(L1): {}", name);
    }

    @Override
    public List<Tool> tools() {
        if (!toolsLoaded) {
            loadTools();
        }
        return tools;
    }

    @Override
    public boolean isToolsLoaded() {
        return toolsLoaded;
    }

    @Override
    public void loadTools() {
        // FileSkill 默认无专属工具(纯指令型),子类可覆盖
        toolsLoaded = true;
    }

    public Path getSkillDir() {
        return skillDir;
    }

    public Path getSkillFile() {
        return skillFile;
    }
}
