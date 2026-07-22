# ReAct Agent 引擎

> 基于 Spring Boot 手搓的 ReAct 模式智能体引擎。  
> GitHub: https://github.com/Janice0721/react-agent

## 项目简介

一套自研 ReAct Agent 引擎，以 Spring Boot 3.3 + JDK 21 + Project Reactor 为底座，提供：

- **ReAct 闭环**: 思考→行动→观察→继续思考，自主多轮推理
- **分层记忆**: 短期(MySQL) → 中期(Redis 摘要) → 长期(Qdrant 向量检索)
- **工具生态**: Bash/文件/Grep/脚本 + Function Calling + MCP 接入
- **Skill 体系**: 渐进式披露、按需加载、多技能组合
- **Human-in-the-Loop**: 工具审批、主动提问、暂停/恢复
- **长任务治理**: 上下文压缩、异常重试、预算控制、断点续传
- **自定义模型**: OpenAI 兼容协议，用户填 base_url + key + model
- **全链路响应式**: Reactor Flux 流式，WebSocket 双模式

## 模块实现状态

| 模块 | 说明 | 状态 |
|---|---|---|
| react-agent-core | 核心领域模型: Msg/ContentBlock/AgentEvent/Tool/Skill | ✅ 已实现 |
| react-agent-model | 模型适配: OpenAI 兼容(stream/call/embed) | ✅ 已实现 |
| react-agent-tools | 工具生态: ToolKit + 7 个内置工具 | ✅ 已实现 |
| react-agent-skills | Skill 体系: 注册/渐进披露/3 个内置技能 | ✅ 已实现 |
| react-agent-memory | 分层记忆: MySQL + Redis + Qdrant | ✅ 已实现 |
| react-agent-hitl | 人机协同: 暂停/恢复/审批 | ⏳ 待实现 |
| react-agent-runtime | ReAct 引擎: 循环/压缩/重试/Flux | ⏳ 待实现 |
| react-agent-server | 服务层: WebSocket + REST | 🔧 部分实现(启动类) |
| react-agent-example | 可运行示例 | ⏳ 待实现 |

## 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Spring Boot 3.3.5 + JDK 21 |
| 响应式 | Project Reactor (Flux/Mono) |
| 实时通信 | Spring WebSocket |
| 短期记忆 | MySQL 8 + Spring JDBC |
| 中期记忆 | Redis 7 |
| 长期记忆 | Qdrant 向量数据库 |
| 模型接入 | OpenAI 兼容协议 |
| 构建 | Maven 多模块 |

## 文档导航

| 文档 | 说明 |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | 变更日志 |
| [docs/SETUP.md](docs/SETUP.md) | 环境准备: JDK/Maven/MySQL/Redis 安装 |
| [docs/TECHNICAL_DESIGN.md](docs/TECHNICAL_DESIGN.md) | 完整技术方案: 架构、领域模型、各模块设计 |
| [docs/EXECUTION_PLAN.md](docs/EXECUTION_PLAN.md) | 详细执行计划: 11 个阶段，手把手 |
| [docs/modules/](docs/modules/) | 各模块详细文档 |
| [docs/modules/memory.md](docs/modules/memory.md) | 分层记忆模块文档 + 使用指南 |

## 快速开始

### 1. 环境准备

按 [docs/SETUP.md](docs/SETUP.md) 安装 JDK 21、Maven、MySQL、Redis。

额外安装 Qdrant（长期记忆向量库）：

```bash
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 \
  -v $(pwd)/qdrant_data:/qdrant/storage \
  qdrant/qdrant
```

### 2. 配置

编辑 `react-agent-server/src/main/resources/application.yml`，设置模型 API、数据库、Redis、Qdrant 连接信息。

### 3. 编译

```bash
# 确保 JAVA_HOME 指向 JDK 21
export JAVA_HOME=/path/to/jdk-21

mvn install -DskipTests
```

### 4. 启动

```bash
mvn spring-boot:run -pl react-agent-server
```

## 模块结构

```
react-agent/
├── pom.xml                              # 父 POM
├── CHANGELOG.md                         # 变更日志
├── README.md                            # 本文件
├── docs/                                # 文档
│   ├── SETUP.md                         # 环境准备
│   ├── TECHNICAL_DESIGN.md              # 技术方案
│   ├── EXECUTION_PLAN.md                # 执行计划
│   └── modules/                         # 各模块文档
│       ├── core.md
│       ├── model.md
│       ├── tools.md
│       ├── skills.md
│       ├── memory.md
│       ├── hitl.md
│       ├── runtime.md
│       ├── server.md
│       └── example.md
├── react-agent-core/                    # ✅ 核心领域模型
├── react-agent-model/                   # ✅ 模型适配层
├── react-agent-tools/                   # ✅ 工具生态
├── react-agent-skills/                  # ✅ Skill 体系
├── react-agent-memory/                  # ✅ 分层记忆
├── react-agent-hitl/                    # ⏳ 人机协同
├── react-agent-runtime/                 # ⏳ ReAct 引擎
├── react-agent-server/                  # 🔧 服务层
└── react-agent-example/                 # ⏳ 示例
```

## 已实现核心能力

### 模型适配 (react-agent-model)

OpenAI 兼容协议适配器，支持任意兼容服务（OpenAI 官方、OneAPI、本地 vLLM 等）：

```java
@Autowired
private ModelAdapter modelAdapter;

// 非流式
Mono<ModelResponse> resp = modelAdapter.call(context, tools, skills);

// 流式
Flux<ModelChunk> chunks = modelAdapter.stream(context, tools, skills);

// 向量化(记忆模块用)
Mono<float[]> embedding = modelAdapter.embed("text");
```

### 工具生态 (react-agent-tools)

7 个内置工具，支持 Function Calling：

| 工具 | 审批 | 说明 |
|---|---|---|
| bash | ✅ | Shell 命令执行，命令黑名单安全拦截 |
| read | ❌ | 文件读取，支持行范围 |
| write | ✅ | 文件写入 |
| edit | ✅ | 精确字符串替换 |
| grep | ❌ | 内容搜索(ripgrep/grep) |
| glob | ❌ | 文件名匹配 |
| script | ✅ | Python/Node 脚本执行 |

```java
@Autowired
private ToolKit toolKit;

// 转换为 OpenAI Function Calling 格式
List<FunctionDef> specs = toolKit.toFunctionSpecs();

// 执行工具
ToolResult result = toolKit.invoke(toolCallBlock, toolContext);
```

### Skill 体系 (react-agent-skills)

渐进式披露：L0 元数据(始终加载) → L1 指令(按需) → L2 工具(执行时)。  
内置 3 个技能：code-review、data-analysis、document-writing。  
用户可在 `resources/skills/` 下创建 SKILL.md 自定义技能。

### 分层记忆 (react-agent-memory)

```java
@Autowired
private MemoryManager memoryManager;

// 写入短期记忆(落库 MySQL)
memoryManager.addShortTerm(msg);

// 组装上下文(短期 + 中期摘要 + 长期检索)
List<Msg> context = memoryManager.buildContext(sessionId, userId, query);

// 会话结束沉淀长期记忆
memoryManager.consolidate(sessionId, userId, finalReply);
```

详见 [docs/modules/memory.md](docs/modules/memory.md)。

## 配置示例

`react-agent-server/src/main/resources/application.yml`:

```yaml
server:
  port: 9999

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/react_agent?useSSL=false&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

agent:
  model:
    base-url: ${MODEL_BASE_URL:https://api.openai.com/v1}
    api-key: ${MODEL_API_KEY:}
    model: ${MODEL_NAME:gpt-4o}
    embedding-model: ${EMBEDDING_MODEL:text-embedding-3-small}
    timeout: 60
  skill:
    base-dir: skills
  memory:
    short-term-limit: 20
    compress-threshold: 8000
    long-term-top-k: 5
    qdrant:
      host: ${QDRANT_HOST:localhost}
      port: ${QDRANT_PORT:6334}
      collection-name: react_agent_memory
      vector-dimension: 1536
      enabled: ${QDRANT_ENABLED:true}
```

## 执行计划概览

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 项目骨架 | ✅ |
| 1 | 核心模型 | ✅ |
| 2 | 模型适配 | ✅ |
| 3 | 工具生态 | ✅ |
| 4 | 分层记忆 | ✅ |
| 5 | Skill 体系 | ✅ |
| 6 | 人机协同 | ⏳ |
| 7 | ReAct 引擎 | ⏳ |
| 8 | 服务层 | 🔧 |
| 9 | 前端 Demo | ⏳ |
| 10 | 集成测试 | ⏳ |
| 11 | 完善优化 | ⏳ |

## License

Apache License 2.0
