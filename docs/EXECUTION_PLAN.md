# ReAct Agent 引擎 — 详细执行计划 (手把手版)

> 本文档面向小白,假设你已按 `SETUP.md` 装好 JDK 21、Maven、MySQL、Redis。  
> 每一步都告诉你: **做什么、在哪个文件、写什么代码、怎么验证**。  
> 按顺序从上到下做,不要跳步。

---

## 全局约定

- 项目根目录: `/Users/user/Desktop/react-agent`
- Java 包名前缀: `com.reactagent`
- 模型配置通过环境变量传入,不在代码里硬编码

---

# 阶段 0: 项目骨架搭建

## 步骤 0.1 — 创建父 POM

**做什么**: 创建 Maven 多模块项目的父配置文件。

**文件**: `react-agent/pom.xml`

**怎么验证**: 在项目根目录执行 `mvn validate`,看到 BUILD SUCCESS。

---

## 步骤 0.2 — 创建 8 个子模块

**做什么**: 创建 8 个子模块目录,每个有自己的 `pom.xml`。

**怎么验证**: 在项目根目录执行 `mvn compile`,所有模块编译通过。

子模块列表:

1. `react-agent-core` — 核心领域模型
2. `react-agent-model` — 模型适配
3. `react-agent-memory` — 分层记忆
4. `react-agent-tools` — 工具生态
5. `react-agent-skills` — Skill 体系
6. `react-agent-hitl` — 人机协同
7. `react-agent-runtime` — ReAct 引擎
8. `react-agent-server` — 服务层(含启动类)
9. `react-agent-example` — 可运行示例

---

# 阶段 1: 核心领域模型 (react-agent-core)

## 步骤 1.1 — 创建 Msg 消息类

**做什么**: 定义对话消息的领域模型。

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/Msg.java`

**内容要点**:
- `record Msg` 含字段: id, sessionId, name, role, content, metadata, createdAt, finishedAt, usage
- `Role` 枚举: USER, ASSISTANT, SYSTEM
- 静态工厂方法: `UserMsg.of()`, `AssistantMsg.of()`, `SystemMsg.of()`

**怎么验证**: `mvn compile -pl react-agent-core` 编译通过。

---

## 步骤 1.2 — 创建 ContentBlock 内容块

**做什么**: 定义消息内容的各种块类型。

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ContentBlock.java`

**内容要点**:
- `sealed interface ContentBlock`
- 6 个 record 实现: TextBlock, ThinkingBlock, ToolCallBlock, ToolResultBlock, DataBlock, HintBlock
- 两个状态枚举: ToolCallState, ToolResultState

**怎么验证**: 编译通过。

---

## 步骤 1.3 — 创建 AgentEvent 事件类

**做什么**: 定义流式推送的事件模型。

**文件**: `react-agent-core/src/main/java/com/reactagent/core/event/AgentEvent.java`

**内容要点**:
- `record AgentEvent`: id, replyId, sessionId, type, blockId, toolCallId, payload, createdAt
- `EventType` 枚举: 所有事件类型
- 静态工厂方法: `AgentEvent.of(type, replyId, ...)`

---

## 步骤 1.4 — 创建 Usage 统计类

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/Usage.java`

```java
public record Usage(int promptTokens, int completionTokens, int totalTokens) {}
```

---

## 步骤 1.5 — 创建 Tool 工具抽象

**文件**: `react-agent-core/src/main/java/com/reactagent/core/tool/Tool.java`

```java
public interface Tool {
    String name();
    String description();
    JsonNode schema();
    ToolResult invoke(JsonNode input, ToolContext ctx);
}
```

同时创建:
- `ToolResult.java` (record: id, success, output, metadata)
- `ToolContext.java` (record: sessionId, workDir, timeoutSeconds, env)
- `ToolCallBlock` 相关状态枚举(如未在 1.2 创建)

---

## 步骤 1.6 — 创建 Skill 抽象

**文件**: `react-agent-core/src/main/java/com/reactagent/core/skill/Skill.java`

**内容要点**:
- `class Skill`: name, description, whenToUse, instructions, tools, resources, loaded 标志
- `record SkillMeta(name, description, whenToUse)`

---

# 阶段 2: 模型适配层 (react-agent-model)

## 步骤 2.1 — 创建 ModelAdapter 接口

**文件**: `react-agent-model/src/main/java/com/reactagent/model/ModelAdapter.java`

```java
public interface ModelAdapter {
    Flux<ModelChunk> stream(List<Msg> context, List<FunctionDef> tools);
    Mono<ModelResponse> call(List<Msg> context, List<FunctionDef> tools);
    Mono<float[]> embed(String text);
}
```

同时创建 DTO:
- `ModelChunk.java` — 流式块(text delta / tool_call delta / thinking delta)
- `ModelResponse.java` — 完整响应(text, toolCalls, usage, thinking)
- `FunctionDef.java` — 函数定义(name, description, parameters schema)

---

## 步骤 2.2 — 创建 OpenAICompatibleAdapter

**文件**: `react-agent-model/src/main/java/com/reactagent/model/OpenAICompatibleAdapter.java`

**做什么**: 实现 OpenAI `/v1/chat/completions` 调用,支持流式和非流式。

**关键实现点**:

1. **构造函数**: 接收 baseUrl, apiKey, model, embeddingModel
2. **WebClient 配置**: Spring WebFlux 的 WebClient,设置超时
3. **stream() 方法**:
   - POST `{baseUrl}/chat/completions`
   - header: `Authorization: Bearer {apiKey}`
   - body: model, messages(转换), tools(转换), stream=true
   - 解析 SSE 流: 每行 `data: {...}`,解析 delta
4. **call() 方法**: 同上但 stream=false,返回完整响应
5. **embed() 方法**: POST `{baseUrl}/embeddings`
6. **消息转换**: `Msg` → OpenAI message 格式(role + content + tool_calls)
7. **工具转换**: `FunctionDef` → OpenAI tools 格式
8. **响应解析**: 解析 choices[0].message,提取 text/tool_calls/usage

**怎么验证**: 写一个临时 main 方法,用你的 API key 调用一次,打印响应。

---

## 步骤 2.3 — 创建 ModelConfig 配置类

**文件**: `react-agent-model/src/main/java/com/reactagent/model/ModelConfig.java`

**做什么**: 从 application.yml 读取配置,创建 ModelAdapter Bean。

```java
@Configuration
public class ModelConfig {
    @Bean
    public ModelAdapter modelAdapter(
        @Value("${agent.model.base-url}") String baseUrl,
        @Value("${agent.model.api-key}") String apiKey,
        @Value("${agent.model.model}") String model,
        @Value("${agent.model.embedding-model:text-embedding-3-small}") String embeddingModel
    ) {
        return new OpenAICompatibleAdapter(baseUrl, apiKey, model, embeddingModel);
    }
}
```

---

# 阶段 3: 工具生态 (react-agent-tools)

## 步骤 3.1 — 创建 Toolkit 工具箱

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/Toolkit.java`

**做什么**: 管理所有工具的注册、查询、执行、审批标记。

**关键方法**:
- `register(Tool tool)` — 注册工具
- `markApprovalRequired(String name)` — 标记需审批
- `needsApproval(String name)` — 查询是否需审批
- `toFunctionSpecs()` — 转换为 FunctionDef 列表
- `invoke(ToolCallBlock call, ToolContext ctx)` — 执行工具调用

---

## 步骤 3.2 — 实现 BashTool

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/BashTool.java`

**做什么**: 执行 shell 命令。

**关键实现**:
- 用 `ProcessBuilder` 启动命令
- 设置工作目录和超时
- 捕获 stdout + stderr
- 返回 ToolResult

```java
public class BashTool implements Tool {
    public String name() { return "bash"; }
    public String description() { return "在终端执行 shell 命令"; }
    public JsonNode schema() {
        // JSON Schema: { "type":"object", "properties": { "command": {"type":"string"} }, "required":["command"] }
    }
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        String command = input.get("command").asText();
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(new File(ctx.workDir()));
        Process p = pb.start();
        String output = readOutput(p);
        boolean finished = p.waitFor(ctx.timeoutSeconds(), TimeUnit.SECONDS);
        // 处理超时
        return new ToolResult(...);
    }
}
```

**怎么验证**: 写临时 main,执行 `echo hello`,确认输出 "hello"。

---

## 步骤 3.3 — 实现 ReadTool / WriteTool / EditTool

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/ReadTool.java`  
**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/WriteTool.java`  
**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/EditTool.java`

**ReadTool**: 读取文件内容,参数: path, 可选 offset/limit  
**WriteTool**: 写入文件,参数: path, content  
**EditTool**: 字符串替换,参数: path, oldString, newString

---

## 步骤 3.4 — 实现 GrepTool / GlobTool

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/GrepTool.java`  
**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/GlobTool.java`

**GrepTool**: 调用 `rg` 命令搜索内容  
**GlobTool**: 用 Java NIO `Files.walk` + glob 匹配文件名

---

## 步骤 3.5 — 实现 ScriptTool

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/impl/ScriptTool.java`

**做什么**: 运行 Python 或 Node 脚本。

**参数**: language (python/node), code  
**实现**: ProcessBuilder 调用 `python3 -c` 或 `node -e`

---

## 步骤 3.6 — 实现 MCP Client

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/mcp/McpClient.java`  
**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/mcp/McpTransport.java`

**做什么**: 连接外部 MCP Server,拉取工具,代理调用。

**McpTransport 接口**:
- `StdioTransport` — 通过子进程 stdio 通信(JSON-RPC)
- `SseTransport` — 通过 SSE 通信

**McpClient 方法**:
- `listTools()` — 调用 `tools/list` 方法,返回工具列表
- `callTool(name, args)` — 调用 `tools/call` 方法

**McpToolAdapter**: 把 MCP 工具适配为 `Tool` 接口。

---

## 步骤 3.7 — 工具自动注册配置

**文件**: `react-agent-tools/src/main/java/com/reactagent/tools/ToolConfig.java`

**做什么**: Spring 配置类,创建 Toolkit Bean,注册所有内置工具。

```java
@Configuration
public class ToolConfig {
    @Bean
    public Toolkit toolkit() {
        Toolkit tk = new Toolkit();
        tk.register(new BashTool());
        tk.register(new ReadTool());
        tk.register(new WriteTool());
        tk.register(new EditTool());
        tk.register(new GrepTool());
        tk.register(new GlobTool());
        tk.register(new ScriptTool());
        // 标记需审批的工具
        tk.markApprovalRequired("bash");
        tk.markApprovalRequired("write");
        tk.markApprovalRequired("edit");
        tk.markApprovalRequired("script");
        return tk;
    }
}
```

---

# 阶段 4: 分层记忆 (react-agent-memory)

## 步骤 4.1 — 创建 MemoryManager 接口

**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/MemoryManager.java`

```java
public interface MemoryManager {
    void addShortTerm(Msg msg);
    List<Msg> buildContext(Msg currentQuery);
    void consolidate(Msg finalReply);
    List<Msg> retrieve(String query, int topK);
    List<Msg> loadHistory(String sessionId);
    int estimateTokens();
}
```

---

## 步骤 4.2 — 实现 ShortTermMemory

**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/ShortTermMemory.java`

**做什么**: 内存中的 RingBuffer,保存最近 N 轮消息。

**关键字段**:
- `Deque<Msg> messages` — 双端队列
- `int capacity` — 最大轮数(默认 20)

**关键方法**:
- `add(Msg)` — 加到队尾,超容量时队首出队(返回给中期压缩)
- `getAll()` — 返回全部
- `takeOld(int keepRecent)` — 取出旧消息用于压缩

---

## 步骤 4.3 — 实现 MidTermMemory

**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/MidTermMemory.java`

**做什么**: Redis 存储,会话级摘要。

**Redis 操作**:
- Key: `session:{sessionId}:midterm`
- Value: 摘要文本(JSON 列表)
- TTL: 24 小时

**关键方法**:
- `store(String sessionId, Summary summary)`
- `get(String sessionId)` → `List<Summary>`
- `clear(String sessionId)`

---

## 步骤 4.4 — 实现 LongTermMemory

**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/LongTermMemory.java`

**做什么**: MySQL 存储 + 向量检索。

**关键方法**:
- `store(Msg msg)` — 调用 `model.embed()` 生成向量,存入 MySQL
- `search(String query, int topK)` — embed 查询,全量计算余弦相似度,返回 top-k

**SQL**:
```sql
INSERT INTO agent_memory_long (id, user_id, session_id, content, embedding, metadata, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?);
```

**检索**: 查出该用户全部记忆 → 逐条算余弦相似度 → 排序取 top-k。  
(中小规模够用,后续可优化为向量索引)

---

## 步骤 4.5 — 实现 MemoryManagerImpl

**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/MemoryManagerImpl.java`

**做什么**: 组合三层记忆,实现完整接口。

**buildContext() 逻辑**:
1. 取短期记忆全部消息
2. 取中期记忆摘要,转为 system hint 消息,放最前面
3. 用 currentQuery 检索长期记忆 top-5,转为 hint 消息
4. 组装返回: [中期摘要hints] + [长期检索hints] + [短期原文]

**consolidate() 逻辑**:
1. 当前回复 + 关键信息 → 调 LLM 提取记忆要点
2. 存入长期记忆(MySQL + embedding)

---

## 步骤 4.6 — 创建记忆相关实体和 Repository

**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/entity/LongTermMemoryEntity.java`  
**文件**: `react-agent-memory/src/main/java/com/reactagent/memory/repository/MessageRepository.java`

**做什么**: 用 Spring Data JDBC 或 MyBatis 操作 MySQL。

> 推荐用 Spring Data JDBC(轻量,不需要 JPA 那么多重型配置)。

---

# 阶段 5: Skill 体系 (react-agent-skills)

## 步骤 5.1 — 实现 SkillRegistry

**文件**: `react-agent-skills/src/main/java/com/reactagent/skills/SkillRegistry.java`

**关键方法**:
- `register(Skill skill)` — 注册
- `listMeta()` — 返回所有 Skill 的 L0 元数据
- `load(String name)` — 加载 L1 指令 + L2 工具
- `compose(List<String> names)` — 组合多个 Skill

---

## 步骤 5.2 — 实现 SkillLoader

**文件**: `react-agent-skills/src/main/java/com/reactagent/skills/SkillLoader.java`

**做什么**: 从 `resources/skills/` 目录扫描加载 Skill。

**加载流程**:
1. 扫描 `resources/skills/*/SKILL.md`
2. 解析 front matter(name, description, whenToUse)
3. 创建 Skill 对象(L0 已加载, L1/L2 延迟)
4. 注册到 SkillRegistry

---

## 步骤 5.3 — 创建内置 Skill 示例

**文件**: `react-agent-skills/src/main/resources/skills/code-review/SKILL.md`

```markdown
---
name: code-review
description: 代码审查技能
whenToUse: 当用户要求审查代码质量、发现 bug、改进建议时
---

# 代码审查技能

## 工作流程
1. 读取目标文件
2. 分析代码结构和逻辑
3. 检查常见问题(空指针、资源泄漏、异常处理)
4. 输出审查报告

## 输出格式
- 严重问题(必须修复)
- 建议改进(推荐修复)
- 良好实践(保持)
```

再创建 2-3 个示例 Skill(data-analysis, document-writing)。

---

## 步骤 5.4 — 实现 LoadSkillTool

**文件**: `react-agent-skills/src/main/java/com/reactagent/skills/LoadSkillTool.java`

**做什么**: 一个特殊的 Tool,模型调用它来按需加载 Skill。

```java
public class LoadSkillTool implements Tool {
    public String name() { return "load_skill"; }
    public String description() {
        return "按需加载技能的完整指令。可用技能: " + registry.listMeta();
    }
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        Skill s = registry.load(input.get("name").asText());
        return ToolResult.success(s.getInstructions());
    }
}
```

---

# 阶段 6: 人机协同 (react-agent-hitl)

## 步骤 6.1 — 创建 SessionState 枚举

**文件**: `react-agent-hitl/src/main/java/com/reactagent/hitl/SessionState.java`

```java
public enum SessionState { RUNNING, SUSPENDED, ABORTED, ENDED }
```

---

## 步骤 6.2 — 实现 HitlManager

**文件**: `react-agent-hitl/src/main/java/com/reactagent/hitl/HitlManager.java`

**做什么**: 管理工具审批和主动提问的暂停/恢复。

**核心字段**:
- `Map<String, CompletableFuture<Boolean>> pendingApprovals`
- `Map<String, CompletableFuture<String>> pendingQuestions`

**核心方法**:
- `requestPermission(ToolCallBlock call, FluxSink<AgentEvent> sink)` → `Mono<Boolean>`
- `askUser(String question, FluxSink<AgentEvent> sink)` → `Mono<String>`
- `resumeApproval(String id, boolean approved)` — 前端回调
- `resumeAnswer(String id, String answer)` — 前端回调

**暂停机制**: `CompletableFuture` 阻塞 Agent 线程,前端 WebSocket 回调后 complete。

---

## 步骤 6.3 — 实现 SessionManager

**文件**: `react-agent-hitl/src/main/java/com/reactagent/hitl/SessionManager.java`

**做什么**: 管理会话状态(Redis + MySQL 持久化)。

**方法**:
- `createSession(userId)` → sessionId
- `updateState(sessionId, state)`
- `getState(sessionId)` → SessionState
- `saveCheckpoint(sessionId)` — 保存断点
- `restore(sessionId)` — 恢复会话

---

# 阶段 7: ReAct 引擎 (react-agent-runtime)

## 步骤 7.1 — 实现 EventBus

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/EventBus.java`

**做什么**: 响应式事件总线,用 Reactor Sinks 实现。

```java
public class EventBus {
    private final Map<String, Sinks.Many<AgentEvent>> sessionSinks = new ConcurrentHashMap<>();

    public Flux<AgentEvent> subscribe(String sessionId) {
        return sessionSinks.computeIfAbsent(sessionId,
            k -> Sinks.many().multicast().onBackpressureBuffer()).asFlux();
    }

    public void publish(String sessionId, AgentEvent event) {
        var sink = sessionSinks.get(sessionId);
        if (sink != null) sink.tryEmitNext(event);
    }

    public void complete(String sessionId) {
        var sink = sessionSinks.get(sessionId);
        if (sink != null) sink.tryEmitComplete();
    }
}
```

---

## 步骤 7.2 — 实现 ContextCompactor

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/ContextCompactor.java`

**做什么**: 上下文压缩器。

**方法**:
- `shouldCompress(MemoryManager memory)` — token 超阈值?
- `compress(MemoryManager memory)` — 取旧消息 → LLM 摘要 → 存中期

---

## 步骤 7.3 — 实现 RetryPolicy

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/RetryPolicy.java`

**做什么**: 指数退避重试。

```java
public class RetryPolicy {
    int maxRetries = 3;
    long initialBackoffMs = 1000;
    double multiplier = 2.0;

    public <T> T execute(Supplier<T> action) {
        int attempt = 0;
        long backoff = initialBackoffMs;
        while (true) {
            try { return action.get(); }
            catch (Exception e) {
                if (++attempt > maxRetries) throw new RuntimeException(e);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {}
                backoff *= multiplier;
            }
        }
    }
}
```

---

## 步骤 7.4 — 实现 BudgetControl

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/BudgetControl.java`

**做什么**: 单 reply token 预算控制。

**逻辑**: 累计 token 超阈值 → 注入 HintBlock 提醒收尾 → 下轮强制 tool_choice=none。

---

## 步骤 7.5 — 实现 ReActLoop

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/ReActLoop.java`

**做什么**: ReAct 核心循环(思考-行动-观察)。

**这是最核心的类**,伪代码见技术方案文档第 3.3 节。

**关键流程**:
1. 写入短期记忆
2. while (iter < maxIters):
   a. 压缩检查
   b. 预算检查
   c. 组装上下文
   d. 调模型(流式,边收边推事件)
   e. 有工具调用? → 审批 → 执行(重试) → 观察写回 → continue
   f. 无工具调用 → break
3. 记忆沉淀
4. REPLY_END

---

## 步骤 7.6 — 实现 ReActAgent

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/ReActAgent.java`

**做什么**: Agent 门面,组合所有组件。

```java
public class ReActAgent {
    // 注入所有依赖
    public Flux<AgentEvent> replyStream(Msg userMsg) {
        return Flux.create(sink -> {
            ReActLoop loop = new ReActLoop(/* 所有依赖 */, sink);
            loop.run(userMsg);
        });
    }
}
```

---

## 步骤 7.7 — Agent 装配配置类

**文件**: `react-agent-runtime/src/main/java/com/reactagent/runtime/AgentConfig.java`

**做什么**: Spring 配置,装配 ReActAgent Bean。

```java
@Configuration
public class AgentConfig {
    @Bean
    public ReActAgent reactAgent(ModelAdapter model, Toolkit toolkit,
            MemoryManager memory, SkillRegistry skills,
            HitlManager hitl, EventBus eventBus) {
        return ReActAgent.builder()
            .model(model).toolkit(toolkit).memory(memory)
            .skills(skills).hitl(hitl).eventBus(eventBus)
            .maxIters(25)
            .build();
    }
}
```

---

# 阶段 8: 服务层 (react-agent-server)

## 步骤 8.1 — 创建 Spring Boot 启动类

**文件**: `react-agent-server/src/main/java/com/reactagent/server/ReactAgentApplication.java`

```java
@SpringBootApplication
public class ReactAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactAgentApplication.class, args);
    }
}
```

---

## 步骤 8.2 — 创建 application.yml

**文件**: `react-agent-server/src/main/resources/application.yml`

**内容**: 技术方案文档第十一章的完整配置。  
注意 MySQL/Redis 的连接信息要对齐 SETUP.md 里创建的。

---

## 步骤 8.3 — 创建数据库初始化脚本

**文件**: `react-agent-server/src/main/resources/schema.sql`

**内容**: 技术方案文档第十二章的建表 SQL。  
配置 Spring Boot 启动时自动执行(`spring.sql.init.mode: always`)。

---

## 步骤 8.4 — 实现 WebSocketHandler

**文件**: `react-agent-server/src/main/java/com/reactagent/server/ws/AgentWebSocketHandler.java`

**做什么**: WebSocket 端点,接收前端消息,路由处理,推送事件。

**关键逻辑**:
1. 收到 `user_message` → 调 `agent.replyStream()` → 推回事件
2. 收到 `hitl_resume` → 调 `hitlManager.resumeApproval()`
3. 收到 `hitl_answer` → 调 `hitlManager.resumeAnswer()`
4. 收到 `pause` → 暂停当前会话
5. 收到 `abort` → 中止当前会话

---

## 步骤 8.5 — 实现 WebSocketConfig

**文件**: `react-agent-server/src/main/java/com/reactagent/server/ws/WebSocketConfig.java`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler(), "/ws/agent")
                .setAllowedOrigins("*");
    }
}
```

---

## 步骤 8.6 — 实现 REST 接口 (可选)

**文件**: `react-agent-server/src/main/java/com/reactagent/server/controller/AgentController.java`

**做什么**: 提供 REST 接口(非流式对话、会话管理)。

```
POST /api/agent/chat          — 非流式对话
POST /api/session             — 创建会话
GET  /api/session/{id}/history — 获取历史
POST /api/session/{id}/abort  — 中止会话
```

---

# 阶段 9: 前端 Demo

## 步骤 9.1 — 创建聊天 HTML 页面

**文件**: `react-agent-server/src/main/resources/static/index.html`

**做什么**: 最简单的单页 Demo,功能:
- WebSocket 连接
- 消息输入框 + 发送按钮
- 消息显示区(区分用户/助手/工具/思考)
- 审批弹窗(工具调用时弹出 approve/deny)
- 提问弹窗(Agent 主动提问时弹出输入框)

**技术**: 纯 HTML + 原生 JS + 少量 CSS,不引入框架。  
约 200-300 行代码。

---

# 阶段 10: 集成测试与联调

## 步骤 10.1 — 启动服务

```bash
# 设置模型环境变量
export MODEL_BASE_URL=https://api.openai.com/v1
export MODEL_API_KEY=sk-你的key
export MODEL_NAME=gpt-4o

# 启动
cd /Users/user/Desktop/react-agent
mvn spring-boot:run -pl react-agent-server
```

---

## 步骤 10.2 — 测试基础对话

1. 浏览器打开 `http://localhost:8080`
2. 输入 "你好",确认收到流式回复
3. 确认思考内容(text_block_delta)和最终回复正常显示

---

## 步骤 10.3 — 测试工具调用

1. 输入 "帮我查看当前目录有哪些文件"
2. 确认 Agent 调用 bash 工具
3. 确认弹出审批弹窗
4. 点击 approve,确认工具执行结果返回

---

## 步骤 10.4 — 测试 Skill 加载

1. 输入 "帮我审查 xxx 文件的代码"
2. 确认 Agent 调用 load_skill("code-review")
3. 确认技能指令加载,按技能流程执行

---

## 步骤 10.5 — 测试记忆

1. 第一轮对话告诉 Agent "我叫张三"
2. 新建会话
3. 问 "我叫什么名字"
4. 确认 Agent 能从长期记忆中检索到

---

# 阶段 11: 完善与优化

## 步骤 11.1 — 添加全局异常处理
## 步骤 11.2 — 添加日志和监控
## 步骤 11.3 — 添加单元测试
## 步骤 11.4 — 编写 README
## 步骤 11.5 — 打包验证 `mvn package`

---

> 每个阶段的详细代码实现,在执行到该步骤时可以让我帮你生成具体代码。  
> 现在先确保整体方案和计划你理解了,然后从阶段 0 开始逐步执行。

---

# 附录 A: 阶段 0 详细操作 (手把手)

## A.1 配置 Maven 国内镜像

**为什么要做**: Maven 默认从国外下载依赖,很慢。配阿里云镜像可以快几十倍。

**文件**: `~/.m2/settings.xml` (如果不存在就创建)

**操作步骤**:

```bash
# 1. 创建 .m2 目录(如果不存在)
mkdir -p ~/.m2

# 2. 创建 settings.xml
cat > ~/.m2/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
          https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyunmaven</id>
      <mirrorOf>*</mirrorOf>
      <name>阿里云公共仓库</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

# 3. 验证文件创建成功
cat ~/.m2/settings.xml
```

---

## A.2 创建父 pom.xml

**文件**: `/Users/user/Desktop/react-agent/pom.xml`

**完整内容** (直接复制):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 父项目坐标 -->
    <groupId>com.reactagent</groupId>
    <artifactId>react-agent-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>ReAct Agent Engine</name>
    <description>自研 ReAct Agent 引擎</description>

    <!-- 子模块 -->
    <modules>
        <module>react-agent-core</module>
        <module>react-agent-model</module>
        <module>react-agent-memory</module>
        <module>react-agent-tools</module>
        <module>react-agent-skills</module>
        <module>react-agent-hitl</module>
        <module>react-agent-runtime</module>
        <module>react-agent-server</module>
        <module>react-agent-example</module>
    </modules>

    <!-- 版本统一管理 -->
    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <spring-boot.version>3.3.5</spring-boot.version>
        <jackson.version>2.17.2</jackson.version>
        <slf4j.version>2.0.13</slf4j.version>
    </properties>

    <!-- 依赖版本声明(子模块按需引用) -->
    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 内部模块互相引用 -->
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-core</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-model</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-memory</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-tools</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-skills</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-hitl</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.reactagent</groupId>
                <artifactId>react-agent-runtime</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- 所有子模块共享的依赖 -->
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>

    <!-- 编译插件 -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**怎么验证**:

```bash
cd /Users/user/Desktop/react-agent
mvn validate
# 看到 BUILD SUCCESS 说明父 POM 正确
# (此时子模块还没创建,会报错,下一步创建)
```

---

## A.3 创建子模块目录和 pom.xml

对每个子模块,创建目录结构和 pom.xml。下面以 `react-agent-core` 为例:

**目录**:

```bash
mkdir -p react-agent-core/src/main/java/com/reactagent/core/msg
mkdir -p react-agent-core/src/main/java/com/reactagent/core/event
mkdir -p react-agent-core/src/main/java/com/reactagent/core/tool
mkdir -p react-agent-core/src/main/java/com/reactagent/core/skill
```

**文件**: `react-agent-core/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.reactagent</groupId>
        <artifactId>react-agent-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>react-agent-core</artifactId>
    <name>ReAct Agent Core</name>

    <dependencies>
        <!-- Jackson JSON 处理 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

其他模块同理,区别在于:
- `react-agent-model`: 依赖 core + spring-webflux
- `react-agent-memory`: 依赖 core + model + spring-data-redis + spring-data-jdbc + mysql-connector
- `react-agent-tools`: 依赖 core
- `react-agent-skills`: 依赖 core + tools
- `react-agent-hitl`: 依赖 core + redis
- `react-agent-runtime`: 依赖 core + model + memory + tools + skills + hitl + reactor
- `react-agent-server`: 依赖 runtime + spring-boot-starter-websocket + spring-boot-starter-data-redis + spring-boot-starter-jdbc
- `react-agent-example`: 依赖 server

**怎么验证**:

```bash
cd /Users/user/Desktop/react-agent
mvn compile
# 看到 BUILD SUCCESS,所有模块编译通过(此时还没有代码,但目录结构正确)
```

---

# 附录 B: 阶段 1 详细操作 (核心模型代码)

## B.1 ContentBlock.java

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ContentBlock.java`

```java
package com.reactagent.core.msg;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 内容块接口,消息由多个内容块组成。
 * 使用 sealed interface 限定实现类型。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextBlock.class, name = "text"),
    @JsonSubTypes.Type(value = ThinkingBlock.class, name = "thinking"),
    @JsonSubTypes.Type(value = ToolCallBlock.class, name = "tool_call"),
    @JsonSubTypes.Type(value = ToolResultBlock.class, name = "tool_result"),
    @JsonSubTypes.Type(value = DataBlock.class, name = "data"),
    @JsonSubTypes.Type(value = HintBlock.class, name = "hint")
})
public sealed interface ContentBlock
    permits TextBlock, ThinkingBlock, ToolCallBlock,
            ToolResultBlock, DataBlock, HintBlock {

    /** 获取块类型字符串 */
    String type();
}
```

## B.2 各内容块实现

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/TextBlock.java`

```java
package com.reactagent.core.msg;

public record TextBlock(String text) implements ContentBlock {
    @Override
    public String type() { return "text"; }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ThinkingBlock.java`

```java
package com.reactagent.core.msg;

public record ThinkingBlock(String thinking) implements ContentBlock {
    @Override
    public String type() { return "thinking"; }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ToolCallBlock.java`

```java
package com.reactagent.core.msg;

/**
 * 工具调用块
 * @param id     工具调用唯一 ID
 * @param name   工具名称
 * @param input  参数(JSON 字符串)
 * @param state  调用状态
 */
public record ToolCallBlock(
    String id,
    String name,
    String input,
    ToolCallState state
) implements ContentBlock {
    @Override
    public String type() { return "tool_call"; }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ToolCallState.java`

```java
package com.reactagent.core.msg;

public enum ToolCallState {
    PENDING,     // 待执行
    ASKING,      // 等待用户审批
    ALLOWED,     // 已批准
    SUBMITTED,   // 已提交执行
    FINISHED     // 已完成
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ToolResultBlock.java`

```java
package com.reactagent.core.msg;

public record ToolResultBlock(
    String id,           // 对应的 ToolCallBlock.id
    String name,
    String output,
    ToolResultState state
) implements ContentBlock {
    @Override
    public String type() { return "tool_result"; }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/ToolResultState.java`

```java
package com.reactagent.core.msg;

public enum ToolResultState {
    RUNNING, SUCCESS, ERROR, INTERRUPTED, DENIED
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/DataBlock.java`

```java
package com.reactagent.core.msg;

public record DataBlock(String mediaType, String data) implements ContentBlock {
    @Override
    public String type() { return "data"; }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/HintBlock.java`

```java
package com.reactagent.core.msg;

/**
 * 提示块,用于向 LLM 注入提示信息(如压缩摘要、记忆检索结果)
 */
public record HintBlock(String hint, String source) implements ContentBlock {
    @Override
    public String type() { return "hint"; }
}
```

## B.3 Msg.java

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/Msg.java`

```java
package com.reactagent.core.msg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 消息:对话的一轮,持久化和上下文管理的基本单位。
 */
public record Msg(
    String id,
    String sessionId,
    String name,
    Role role,
    List<ContentBlock> content,
    Map<String, Object> metadata,
    String createdAt,
    String finishedAt,
    Usage usage
) {
    public enum Role { USER, ASSISTANT, SYSTEM }

    // ===== 工厂方法 =====

    public static Msg user(String sessionId, String name, String text) {
        return new Msg(
            UUID.randomUUID().toString(),
            sessionId, name, Role.USER,
            List.of(new TextBlock(text)),
            Map.of(), Instant.now().toString(), null, null
        );
    }

    public static Msg assistant(String sessionId, String name, List<ContentBlock> content) {
        return new Msg(
            UUID.randomUUID().toString(),
            sessionId, name, Role.ASSISTANT,
            content, Map.of(), Instant.now().toString(), null, null
        );
    }

    public static Msg system(String sessionId, String text) {
        return new Msg(
            UUID.randomUUID().toString(),
            sessionId, "system", Role.SYSTEM,
            List.of(new TextBlock(text)),
            Map.of(), Instant.now().toString(), null, null
        );
    }

    public static Msg toolResult(String sessionId, String toolCallId,
                                  String toolName, String output, boolean success) {
        ToolResultState state = success ? ToolResultState.SUCCESS : ToolResultState.ERROR;
        return new Msg(
            UUID.randomUUID().toString(),
            sessionId, "tool", Role.USER,  // OpenAI 格式 tool 结果用 user/tool role
            List.of(new ToolResultBlock(toolCallId, toolName, output, state)),
            Map.of(), Instant.now().toString(), null, null
        );
    }

    // ===== 辅助方法 =====

    /** 获取所有文本内容 */
    public String getTextContent() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            }
        }
        return sb.toString();
    }

    /** 获取所有工具调用 */
    public List<ToolCallBlock> getToolCalls() {
        List<ToolCallBlock> calls = new ArrayList<>();
        for (ContentBlock block : content) {
            if (block instanceof ToolCallBlock tc) {
                calls.add(tc);
            }
        }
        return calls;
    }

    public boolean hasToolCalls() {
        return content.stream().anyMatch(b -> b instanceof ToolCallBlock);
    }
}
```

## B.4 Usage.java

**文件**: `react-agent-core/src/main/java/com/reactagent/core/msg/Usage.java`

```java
package com.reactagent.core.msg;

public record Usage(int promptTokens, int completionTokens, int totalTokens) {
    public static Usage empty() { return new Usage(0, 0, 0); }
}
```

## B.5 AgentEvent.java

**文件**: `react-agent-core/src/main/java/com/reactagent/core/event/AgentEvent.java`

```java
package com.reactagent.core.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件:流式推送给前端的增量单位。
 */
public record AgentEvent(
    String id,
    String replyId,
    String sessionId,
    EventType type,
    String blockId,
    String toolCallId,
    Object payload,
    String createdAt
) {
    public enum EventType {
        // 生命周期
        REPLY_START, REPLY_END, EXCEED_MAX_ITERS,
        // 文本流
        TEXT_BLOCK_START, TEXT_BLOCK_DELTA, TEXT_BLOCK_END,
        // 思考流
        THINKING_BLOCK_START, THINKING_BLOCK_DELTA, THINKING_BLOCK_END,
        // 工具流
        TOOL_CALL_START, TOOL_CALL_DELTA, TOOL_CALL_END,
        TOOL_RESULT_START, TOOL_RESULT_END,
        // 人机协同
        HITL_PERMISSION_REQUEST, HITL_USER_INPUT_REQUEST, HITL_RESUMED,
        // 上下文治理
        CONTEXT_COMPRESSED,
        // 会话控制
        SESSION_PAUSED, SESSION_RESUMED, SESSION_ABORTED,
        // 异常
        ERROR
    }

    // ===== 工厂方法 =====

    public static AgentEvent of(EventType type, String replyId, String sessionId) {
        return new AgentEvent(
            UUID.randomUUID().toString(), replyId, sessionId,
            type, null, null, null, Instant.now().toString()
        );
    }

    public static AgentEvent of(EventType type, String replyId, String sessionId,
                                 String blockId, Object payload) {
        return new AgentEvent(
            UUID.randomUUID().toString(), replyId, sessionId,
            type, blockId, null, payload, Instant.now().toString()
        );
    }

    public static AgentEvent error(String replyId, String sessionId, String message) {
        return new AgentEvent(
            UUID.randomUUID().toString(), replyId, sessionId,
            EventType.ERROR, null, null, message, Instant.now().toString()
        );
    }
}
```

## B.6 Tool 抽象

**文件**: `react-agent-core/src/main/java/com/reactagent/core/tool/Tool.java`

```java
package com.reactagent.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具接口:所有工具(内置/MCP/Skill专属)实现此接口。
 */
public interface Tool {

    /** 工具名称(唯一标识) */
    String name();

    /** 工具描述(给 LLM 看,告诉它这个工具能做什么) */
    String description();

    /** 参数 JSON Schema(告诉 LLM 怎么调用) */
    JsonNode schema();

    /** 执行工具 */
    ToolResult invoke(JsonNode input, ToolContext ctx);
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/tool/ToolResult.java`

```java
package com.reactagent.core.tool;

import java.util.Map;

public record ToolResult(
    String id,
    boolean success,
    String output,
    Map<String, Object> metadata
) {
    public static ToolResult success(String id, String output) {
        return new ToolResult(id, true, output, Map.of());
    }

    public static ToolResult error(String id, String error) {
        return new ToolResult(id, false, error, Map.of());
    }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/tool/ToolContext.java`

```java
package com.reactagent.core.tool;

import java.util.Map;

public record ToolContext(
    String sessionId,
    String workDir,
    int timeoutSeconds,
    Map<String, String> env
) {
    public static ToolContext of(String sessionId, String workDir) {
        return new ToolContext(sessionId, workDir, 30, Map.of());
    }
}
```

## B.7 Skill 抽象

**文件**: `react-agent-core/src/main/java/com/reactagent/core/skill/Skill.java`

```java
package com.reactagent.core.skill;

import com.reactagent.core.tool.Tool;
import java.util.List;
import java.util.Map;

/**
 * Skill:可加载的能力包。
 * 渐进式披露: L0 元数据 → L1 指令 → L2 工具
 */
public class Skill {
    private String name;
    private String description;
    private String whenToUse;           // L0: 告诉模型何时使用

    private String instructions;        // L1: 完整指令文档
    private List<Tool> tools;           // L2: 专属工具
    private Map<String, String> resources; // L2: 资源文件

    private boolean instructionsLoaded = false;
    private boolean toolsLoaded = false;

    // 构造函数、getter、setter 省略...
    // (实际实现时用 IDE 生成)

    public void loadInstructions() {
        if (!instructionsLoaded) {
            // 读取 SKILL.md 内容到 instructions
            instructionsLoaded = true;
        }
    }

    public void loadTools() {
        if (!toolsLoaded) {
            // 实例化专属工具
            toolsLoaded = true;
        }
    }
}
```

**文件**: `react-agent-core/src/main/java/com/reactagent/core/skill/SkillMeta.java`

```java
package com.reactagent.core.skill;

/**
 * Skill 元数据(L0),始终注入 system prompt。
 */
public record SkillMeta(
    String name,
    String description,
    String whenToUse
) {}
```

---

## B.8 验证阶段 1

```bash
cd /Users/user/Desktop/react-agent
mvn compile -pl react-agent-core
# 看到 BUILD SUCCESS 说明核心模型编译通过
```

---

> 附录 C-K (阶段 2-11 的详细代码) 在实际执行到对应阶段时再展开。  
> 当你完成阶段 0-1 后告诉我,我会帮你生成阶段 2 的详细代码。
