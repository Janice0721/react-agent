# 变更日志 (CHANGELOG)

本文件记录 ReAct Agent 引擎的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

---

## [Unreleased]

### 新增
- 项目基础文档：README、SETUP、TECHNICAL_DESIGN、EXECUTION_PLAN

---

## [0.1.0] — 2026-07-22

### 新增 — 项目骨架
- Maven 多模块项目结构（9 个子模块）
- 父 POM 统一依赖管理（Spring Boot 3.3.5、JDK 21、Lombok、Jackson）
- 阿里云 Maven 镜像配置说明

### 新增 — 核心领域模型 (react-agent-core)
- `Msg`：对话消息，含工厂方法（user/assistant/system/toolResult）和辅助方法
- `ContentBlock` 体系：TextBlock、ThinkingBlock、ToolCallBlock、ToolResultBlock、DataBlock、HintBlock
- `ToolCallState` / `ToolResultState` 状态枚举
- `AgentEvent` + `EventType`：全链路流式事件模型（25 种事件类型）
- `Usage`：Token 用量统计
- `Tool` 接口：工具统一抽象（name/description/schema/invoke/approvalRequired）
- `ToolResult`：工具执行结果，含 success/error 工厂方法
- `ToolContext`：工具执行上下文（sessionId/workDir/timeoutSeconds/env），含 Builder
- `Skill` 接口：渐进式披露抽象（L0 元数据 / L1 指令 / L2 工具三层加载）
- `SkillMeta`：技能元数据，含 Builder 和 toString

### 新增 — 模型适配层 (react-agent-model)
- `ModelAdapter` 接口：统一模型调用接口（stream 流式 / call 阻塞 / embed 向量化）
- `OpenAICompatibleAdapter`：OpenAI 兼容协议实现（410 行）
  - SSE 流式解析，支持 text/reasoning_content/tool_calls 增量
  - 非流式完整响应解析
  - embedding 向量化
  - 消息格式转换（Msg → OpenAI message，支持 user/assistant/system/tool 四种 role）
  - 工具格式转换（FunctionDef → OpenAI tools）
  - 全程 onErrorResume 容错
- `FunctionDef`：函数定义 DTO
- `ModelChunk`：流式响应增量块（textDelta/thinkingDelta/toolCall 增量）
- `ModelResponse`：非流式完整响应（text/thinking/toolCalls/usage/finishReason）
- `ModelProperties`：配置属性绑定（agent.model.*）
- `ModelConfig`：Spring 配置类，自动创建 ModelAdapter Bean

### 新增 — 工具生态 (react-agent-tools)
- `ToolKit`：工具箱，线程安全
  - 注册/注销/批量注册
  - 审批标记管理（markApprovalRequired/unmarkApprovalRequired/needsApproval）
  - toFunctionSpecs() 转换为 OpenAI Function Calling 格式
  - invoke() 执行工具调用，全程异常捕获
- `BashTool`：Shell 命令执行，命令黑名单安全拦截，超时控制
- `ReadTool`：文件读取，支持行范围 offset/limit，带行号输出
- `WriteTool`：文件覆盖写入，自动创建父目录
- `EditTool`：精确字符串替换，要求 oldString 唯一匹配
- `GrepTool`：内容搜索，优先 ripgrep 回退 grep，支持 include 过滤
- `GlobTool`：文件名匹配，Java NIO PathMatcher 递归搜索
- `ScriptTool`：Python/Node.js 脚本执行，支持内联 code 和文件 scriptPath
- `ToolConfig`：Spring 配置，自动注册 7 个内置工具

### 新增 — Skill 体系 (react-agent-skills)
- `SkillRegistry`：技能注册中心
  - 注册/注销/查询
  - listMeta() L0 元数据列表
  - buildSkillOverview() 生成技能概览注入 system prompt
  - load() L1 指令按需加载
  - loadTools() L2 工具延迟加载
  - compose() 多技能组合
- `FileSkill`：基于 SKILL.md 的 Skill 实现，YAML front matter 解析
- `SkillLoader`：文件系统扫描器，支持 classpath 和外部目录
- `LoadSkillTool`：模型调用的 load_skill 工具，触发渐进式披露
- `SkillConfig`：Spring 配置，启动时加载内置 + 外部技能
- 内置 3 个示例技能：code-review、data-analysis、document-writing
- 技能自定义说明文档

### 新增 — 分层记忆 (react-agent-memory)
- 三层记忆架构，接口可扩展，后续可演进为中心化记忆服务
- `MemoryManager` 总管接口：buildContext/consolidate/clearSession/compressContext
- `ShortTermMemory` 短期记忆：MySQL 落库，按会话维度持久化
  - `ShortTermMemoryImpl` + `MessageRepository`(JDBC) + `MessageEntity`
  - `MsgSerializer` Msg ↔ Entity 序列化
  - 支持重启恢复、token 估算、旧消息提取
- `MidTermMemory` 中期记忆：Redis 存储会话级摘要
  - `MidTermMemoryImpl`，key=session:{id}:midterm，TTL 24h
  - `MemorySummary` 摘要模型
- `LongTermMemory` 长期记忆：Qdrant 向量数据库
  - `QdrantLongTermMemory`：store/search/delete/clear
  - 自动 embedding + 余弦相似度检索 + 按 userId 隔离
  - Qdrant 未启动时可降级(enabled=false)
- `MemoryManagerImpl` 总管实现：
  - buildContext()：中期摘要 + 长期检索注入 + 短期原文
  - consolidate()：LLM 提取关键信息 → 存 Qdrant
  - compressContext()：token 超阈值 → LLM 摘要 → 存 Redis
- `MemoryProperties` 配置属性绑定(agent.memory.*)
- `MemoryConfig` Spring 配置(QdrantClient Bean)
- 建表脚本 schema.sql(agent_message + agent_session 表)

### 新增 — 服务层 (react-agent-server)
- `ReactAgentApplication`：Spring Boot 启动类
- `application.yml`：模型配置 + 服务端口
- spring-boot-maven-plugin 打包配置

### 文档
- `README.md`：项目总览与文档导航
- `docs/SETUP.md`：环境准备（JDK/Maven/MySQL/Redis 安装指南）
- `docs/TECHNICAL_DESIGN.md`：完整技术方案（1006 行，13 章）
- `docs/EXECUTION_PLAN.md`：详细执行计划（1587 行，11 阶段 + 附录代码）

### 已知问题
- react-agent-hitl、react-agent-runtime、react-agent-example 模块尚未实现，保留骨架
- 未配置 git 账户信息（本次提交修复）
