package com.reactagent.skills;

import com.reactagent.skills.impl.FileSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 技能加载器:从文件系统扫描 SKILL.md 并创建 FileSkill。
 * <p>
 * 扫描约定:
 * <pre>
 *   {skillBaseDir}/
 *   ├── code-review/SKILL.md
 *   ├── data-analysis/SKILL.md
 *   └── document-writing/SKILL.md
 * </pre>
 * <p>
 * 用户只需在技能目录下创建子目录 + SKILL.md 即可添加自定义技能。
 */
@Component
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    /**
     * 从指定根目录扫描加载所有技能。
     *
     * @param skillBaseDir 技能根目录(可为 classpath 路径或文件系统绝对路径)
     * @return 加载的 FileSkill 列表
     */
    public List<FileSkill> loadFromDirectory(String skillBaseDir) {
        List<FileSkill> result = new ArrayList<>();

        Path baseDir = resolveBaseDir(skillBaseDir);
        if (baseDir == null) {
            log.warn("技能目录不存在或无法解析: {}", skillBaseDir);
            return result;
        }

        log.info("扫描技能目录: {}", baseDir);

        try (Stream<Path> stream = Files.walk(baseDir, 2)) {
            stream.filter(Files::isDirectory)
                  .forEach(dir -> {
                      Path skillFile = dir.resolve("SKILL.md");
                      if (Files.exists(skillFile)) {
                          FileSkill skill = new FileSkill(dir, skillFile);
                          skill.parse();
                          if (skill.name() != null && !skill.name().isBlank()) {
                              result.add(skill);
                          }
                      }
                  });
        } catch (Exception e) {
            log.error("扫描技能目录失败: {}", baseDir, e);
        }

        log.info("从 {} 加载到 {} 个技能", baseDir, result.size());
        return result;
    }

    /**
     * 解析技能根目录:优先文件系统,回退 classpath。
     */
    private Path resolveBaseDir(String skillBaseDir) {
        // 1. 尝试作为文件系统绝对路径
        Path fsPath = Paths.get(skillBaseDir);
        if (Files.isDirectory(fsPath)) {
            return fsPath;
        }

        // 2. 尝试当前工作目录下的相对路径
        Path cwdPath = Paths.get(System.getProperty("user.dir")).resolve(skillBaseDir);
        if (Files.isDirectory(cwdPath)) {
            return cwdPath;
        }

        // 3. 尝试 classpath 资源(开发时从 src/main/resources 读取)
        try {
            String classpathDir = skillBaseDir.startsWith("classpath:")
                    ? skillBaseDir.substring("classpath:".length())
                    : skillBaseDir;
            // 通过 ClassLoader 找到 classpath 资源的物理路径
            String resourcePath = classpathDir.startsWith("/")
                    ? classpathDir : "/" + classpathDir;
            java.net.URL url = getClass().getResource(resourcePath);
            if (url != null) {
                Path path = Paths.get(url.toURI());
                if (Files.isDirectory(path)) {
                    return path;
                }
            }
        } catch (Exception e) {
            log.debug("classpath 解析失败: {}", skillBaseDir, e);
        }

        return null;
    }
}
