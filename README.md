# ReAct Agent 引擎

> 基于 Spring Boot 手搓的 ReAct 模式智能体引擎。

## 项目简介

一套自研 ReAct Agent 引擎,以 Spring Boot 3.3 + JDK 21 + Project Reactor 为底座,提供:

- **ReAct 闭环**: 思考→行动→观察→继续思考,自主多轮推理
- **分层记忆**: 短期(内存) → 中期(Redis 摘要) → 长期(MySQL + 向量检索)
- **工具生态**: Bash/文件/Grep/脚本 + Function Calling + MCP 接入
- **Skill 体系**: 渐进式披露、按需加载、多技能组合
- **Human-in-the-Loop**: 工具审批、主动提问、暂停/恢复
- **长任务治理**: 上下文压缩、异常重试、预算控制、断点续传
- **自定义模型**: OpenAI 兼容协议,用户填 base_url + key + model
- **全链路响应式**: Reactor Flux 流式,WebSocket 双模式

## 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Spring Boot 3.3 + JDK 21 |
| 响应式 | Project Reactor (Flux/Mono) |
| 实时通信 | Spring WebSocket |
| 持久化 | MySQL 8 |
| 缓存 | Redis 7 |
| 模型接入 | OpenAI 兼容协议 |
| 构建 | Maven 多模块 |

## 文档导航

| 文档 | 说明 |
|---|---|
| [docs/SETUP.md](docs/SETUP.md) | 环境准备:安装 JDK/Maven/MySQL/Redis,一步步来 |
| [docs/TECHNICAL_DESIGN.md](docs/TECHNICAL_DESIGN.md) | 完整技术方案:架构、领域模型、各模块设计 |
| [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) | 详细执行计划:11 个阶段,手把手每步做什么 |

## 快速开始

### 1. 环境准备

按 [docs/SETUP.md](docs/SETUP.md) 安装 JDK 21、Maven、MySQL、Redis。

### 2. 配置模型

```bash
export MODEL_BASE_URL=https://api.openai.com/v1
export MODEL_API_KEY=sk-你的key
export MODEL_NAME=gpt-4o
```

### 3. 执行构建计划

按 [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) 从阶段 0 开始逐步执行。

### 4. 启动服务 (完成开发后)

```bash
mvn spring-boot:run -pl react-agent-server
```

浏览器打开 `http://localhost:8080` 即可使用 Demo。

## 模块结构

```
react-agent/
├── react-agent-core        # 核心领域模型:Msg/Event/Tool/Skill
├── react-agent-model       # 模型适配:OpenAI 兼容
├── react-agent-memory      # 分层记忆:短/中/长期
├── react-agent-tools      # 工具生态:Bash/File/Grep/Script/MCP
├── react-agent-skills      # Skill 体系:注册/加载/渐进披露
├── react-agent-hitl        # 人机协同:暂停/恢复/审批
├── react-agent-runtime     # ReAct 引擎:循环/压缩/重试/Flux
├── react-agent-server      # 服务层:WebSocket + REST + 配置
└── react-agent-example     # 可运行示例
```

## 执行计划概览

| 阶段 | 内容 | 产出 |
|---|---|---|
| 0 | 项目骨架 | Maven 多模块 + 父 POM |
| 1 | 核心模型 | Msg/Event/Tool/Skill 抽象 |
| 2 | 模型适配 | OpenAI 兼容 Adapter |
| 3 | 工具生态 | 7 个内置工具 + MCP |
| 4 | 分层记忆 | 短/中/长期 + 压缩 |
| 5 | Skill 体系 | 注册/加载/渐进披露 |
| 6 | 人机协同 | 审批/提问/暂停恢复 |
| 7 | ReAct 引擎 | 循环/事件总线/预算 |
| 8 | 服务层 | WebSocket + REST |
| 9 | 前端 Demo | 聊天 + 审批 UI |
| 10 | 集成测试 | 端到端联调 |
| 11 | 完善优化 | 异常/日志/测试 |

## License

Apache License 2.0
