# 技能目录

本目录存放所有技能定义。每个技能一个子目录,包含一个 `SKILL.md` 文件。

## 如何添加自定义技能

### 方法一:在此目录下创建(打包时包含)

1. 在 `skills/` 下创建子目录,如 `skills/my-skill/`
2. 创建 `skills/my-skill/SKILL.md`
3. 重启服务,自动加载

### 方法二:在外部目录创建(不打包,运行时加载)

1. 在任意目录创建技能,如 `~/.react-agent/skills/my-skill/SKILL.md`
2. 在 `application.yml` 中配置:
   ```yaml
   agent:
     skill:
       base-dir: ~/.react-agent/skills
   ```
3. 重启服务

## SKILL.md 格式

```markdown
---
name: my-skill                    # 技能名称(唯一标识)
description: 一句话描述技能功能      # 给模型看
whenToUse: 何时使用此技能的说明      # 帮助模型判断
---

# 技能名称

技能的完整指令文档,模型加载此技能后会看到这些内容。

## 工作流程
1. 第一步...
2. 第二步...

## 输出格式
...
```

## 内置技能

| 技能 | 说明 |
|---|---|
| code-review | 代码审查 |
| data-analysis | 数据分析 |
| document-writing | 文档撰写 |
