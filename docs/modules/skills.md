# react-agent-skills Skill 体系

> 模块路径: `react-agent-skills/`  
> 包名: `com.reactagent.skills`

## 职责

提供技能的注册、渐进式披露加载和多技能组合。支持用户自定义技能。

## 类结构

```
com.reactagent.skills
├── SkillRegistry.java      # 技能注册中心
├── SkillLoader.java        # 文件系统扫描加载器
├── LoadSkillTool.java      # load_skill 工具(模型按需调用)
├── SkillConfig.java        # Spring 配置(自动加载+注册)
└── impl/
    └── FileSkill.java      # 基于 SKILL.md 的 Skill 实现
```

## 渐进式披露模型

```
L0 元数据(name/description/whenToUse) — 始终加载,注入 system prompt
    ↓ 模型调用 load_skill(name)
L1 指令(instructions) — 按需加载,注入上下文
    ↓ 模型按指令执行
L2 工具(tools) — 执行时加载
```

## SkillRegistry 核心方法

| 方法 | 说明 |
|---|---|
| `register(Skill)` / `registerAll(List)` | 注册 |
| `get(name)` / `contains(name)` | 查询 |
| `listMeta()` | L0 元数据列表 |
| `buildSkillOverview()` | 生成技能概览文本 |
| `load(name)` | L1 按需加载 |
| `loadTools(name)` | L2 工具加载 |
| `compose(names)` | 多技能组合 |

## SKILL.md 格式

```markdown
---
name: my-skill
description: 一句话描述
whenToUse: 何时使用
---
# 技能指令正文
...
```

## 内置技能

| 技能 | 说明 |
|---|---|
| code-review | 代码审查 |
| data-analysis | 数据分析 |
| document-writing | 文档撰写 |

## 自定义技能

- **打包内置**: 在 `src/main/resources/skills/` 下创建子目录 + SKILL.md
- **运行时外部**: 配置 `agent.skill.external-dir` 指向外部目录

## 依赖
- react-agent-core
- react-agent-tools (注册 LoadSkillTool)
- spring-boot-starter
