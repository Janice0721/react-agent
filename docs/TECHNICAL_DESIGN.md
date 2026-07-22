# ReAct Agent 引擎 — 技术方案文档

> 版本: v1.0  
> 日期: 2026-07-22  
> 技术栈: Spring Boot 3.3 + JDK 21 + Project Reactor + MySQL + Redis  
> 模型接入: OpenAI 兼容协议 (base_url + api_key + model)

---

## 一、总体架构

### 1.1 架构分层

```
┌──────────────────────────────────────────────────────────┐
│                    前端 Demo (HTML/JS)                    │
│                  WebSocket 客户端 + 聊天 UI               │
└────────────────────────┬─────────────────────────────────┘
                         │ WebSocket (JSON 事件流)
┌────────────────────────▼─────────────────────────────────┐
│                  react-agent-server                       │
│   WebSocket 端点 / REST 接口 / 配置加载 / 会话路由        │
├──────────────────────────────────────────────────────────┤
│                  react-agent-runtime                      │
│   ReAct 引擎 / 事件总线 / Flux 流式 / 重试 / 预算控制     │
├──────────────────────────────────────────────────────────┤
│  react-agent-core   │ react-agent-model  │ react-agent-hitl │
│  Agent/Msg/Event    │ ModelAdapter       │ 暂停/恢复/审批   │
│  Tool/Skill 抽象    │ OpenAI 兼容         │                  │
├─────────────────────┴────────────────────┴──────────────────┤
│  react-agent-memory         │ react-agent-tools  │ react-agent-skills │
│  短期/中期/长期分层         │ Bash/File/Grep/   │ 注册/加载/渐进披露  │
│  MySQL + Redis + 向量       │ Script + MCP      │                    │
└─────────────────────────────┴────────────────────┴───────────────────┘
```

### 1.2 模块划分

```
react-agent/
├── pom.xml                              # 父 POM,管理依赖版本
├── docs/                                # 文档目录(当前)
│   ├── TECHNICAL_DESIGN.md             # 本文档:技术方案
│   ├── EXECUTION_PLAN.md               # 详细执行计划(手把手)
│   └── SETUP.md                        # 环境准备与依赖清单
├── react-agent-core/                    # 核心领域模型与抽象
│   └── src/main/java/.../core/
│       ├── msg/                         # Msg, ContentBlock, Usage
│       ├── event/                       # AgentEvent, EventType
│       ├── tool/                        # Tool, ToolResult, ToolContext
│       └── skill/                       # Skill, SkillMeta 抽象
├── react-agent-model/                   # 模型适配层
│   └── src/main/java/.../model/
│       ├── ModelAdapter.java            # 统一接口
│       ├── OpenAICompatibleAdapter.java # OpenAI 兼容实现
│       └── dto/                         # 请求/响应 DTO
├── react-agent-memory/                  # 分层记忆
│   └── src/main/java/.../memory/
│       ├── MemoryManager.java           # 记忆总管
│       ├── ShortTermMemory.java         # 短期(内存 RingBuffer)
│       ├── MidTermMemory.java           # 中期(Redis 摘要)
│       └── LongTermMemory.java          # 长期(MySQL + 向量)
├── react-agent-tools/                   # 工具生态
│   └── src/main/java/.../tools/
│       ├── Toolkit.java                 # 工具箱
│       ├── impl/                        # Bash/File/Grep/Script
│       └── mcp/                         # MCP Client
├── react-agent-skills/                  # Skill 体系
│   └── src/main/java/.../skills/
│       ├── SkillRegistry.java
│       └── SkillLoader.java
├── react-agent-hitl/                    # 人机协同
│   └── src/main/java/.../hitl/
│       ├── HitlManager.java
│       └── SessionState.java            # RUNNING/SUSPENDED/ABORTED
├── react-agent-runtime/                 # ReAct 引擎
│   └── src/main/java/.../runtime/
│       ├── ReActAgent.java              # 核心 Agent
│       ├── ReActLoop.java               # 循环控制器
│       ├── ContextCompactor.java        # 上下文压缩
│       ├── RetryPolicy.java             # 重试策略
│       └── EventBus.java                # 响应式事件总线
├── react-agent-server/                  # 服务层
│   └── src/main/java/.../server/
│       ├── WebSocketHandler.java        # WebSocket 端点
│       ├── SessionManager.java          # 会话管理
│       ├── config/                      # 配置类
│       └── controller/                  # REST 接口
│   └── src/main/resources/
│       ├── application.yml              # 主配置
│       └── static/                      # 前端 demo 页
└── react-agent-example/                 # 可运行示例
    └── src/main/java/.../example/
        └── ReactAgentApplication.java   # 启动类
```

---

## 二、核心领域模型

### 2.1 消息 (Msg)

消息是对话的一轮,是持久化和上下文管理的基本单位。

```java
public record Msg(
    String id,                    // 唯一 ID (UUID)
    String sessionId,             // 所属会话
    String name,                  // 发送者名称
    Role role,                    // user / assistant / system
    List<ContentBlock> content,   // 内容块列表
    Map<String, Object> metadata, // 任意元数据
    String createdAt,             // 创建时间 ISO 8601
    String finishedAt,            // 完成时间
    Usage usage                   // token 用量
) {
    public enum Role { USER, ASSISTANT, SYSTEM }
}
```

### 2.2 内容块 (ContentBlock)

一条消息由多个内容块组成,使用 sealed interface 限定类型:

```java
public sealed interface ContentBlock
    permits TextBlock, ThinkingBlock, ToolCallBlock,
            ToolResultBlock, DataBlock, HintBlock {}

public record TextBlock(String text) implements ContentBlock {}
public record ThinkingBlock(String thinking) implements ContentBlock {}
public record ToolCallBlock(String id, String name, String input,
                              ToolCallState state) implements ContentBlock {}
public record o(String id, String name, String output,
                              ToolResultState state) implements ContentBlock {}
public record DataBlock(String mediaType, String data) implements ContentBlock {}
public record HintBlock(String hint, String source) implements ContentBlock {}
```

状态枚举:

```java
public enum ToolCallState { PENDING, ASKING, ALLOWED, SUBMITTED, FINISHED }
public enum ToolResultState { RUNNING, SUCCESS, ERROR, INTERRUPTED, DENIED }
```

### 2.3 事件 (AgentEvent)

事件是流式推送给前端的增量单位,贯穿全链路。

```java
public record AgentEvent(
    String id,                    // 事件唯一 ID
    String replyId,               // 关联的回复 ID
    String sessionId,             // 会话 ID
    EventType type,               // 事件类型
    String blockId,               // 关联的内容块 ID
    String toolCallId,            // 关联的工具调用 ID
    Object payload,               // 载荷(文本增量/工具信息等)
    String createdAt
) {}
```

事件类型枚举 (完整):

```java
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
```

---

## 三、ReAct 引擎

### 3.1 核心循环

ReAct = Reasoning + Acting,模型在"思考"和"行动"之间交替,直到产出最终回复。

```
用户输入
  │
  ▼
[写入短期记忆]
  │
  ▼
┌─────────────◄─────────────────────┐
│ [压缩检查] token/轮数超阈值? → 压缩 │
│   │ 否                             │
│   ▼                                │
│ [思考] 组装上下文 → 模型流式调用    │
│   │                                │
│   ▼                                │
│ [判断] 有工具调用?                  │
│   ├─ 有 ──► [审批?] ──► [执行工具] ─┘
│   │          需审批则暂停      观察结果写回
│   └─ 无 ──► [输出最终回复]
  │
  ▼
[记忆沉淀] → REPLY_END
```

### 3.2 ReActAgent 核心结构

```java
public class ReActAgent {
    private final ModelAdapter model;           // 模型适配
    private final Toolkit toolkit;             // 工具箱
    private final MemoryManager memory;        // 记忆管理
    private final SkillRegistry skills;        // Skill 注册
    private final HitlManager hitl;            // 人机协同
    private final ContextCompactor compactor;  // 上下文压缩
    private final RetryPolicy retry;           // 重试
    private final EventBus eventBus;           // 事件总线
    private final int maxIters;                // 最大迭代次数

    /**
     * 流式回复:返回事件流,前端订阅
     */
    public Flux<AgentEvent> replyStream(Msg userMsg) {
        return Flux.create(sink -> {
            var loop = new ReActLoop(this, sink);
            loop.run(userMsg);
        });
    }

    /**
     * 阻塞回复:等待最终 Msg
     */
    public Mono<Msg> reply(Msg userMsg) {
        return replyStream(userMsg)
            .takeUntil(e -> e.type() == EventType.REPLY_END)
            .then(Mono.fromSupplier(() -> currentReply));
    }
}
```

### 3.3 ReActLoop 伪代码

```java
void run(Msg userMsg) {
    // 1. 写入短期记忆
    memory.addShortTerm(userMsg);
    sink.next(event(REPLY_START));

    int iter = 0;
    while (iter++ < maxIters) {
        // 2. 上下文压缩检查
        if (compactor.shouldCompress(memory)) {
            Summary summary = compactor.compress(memory);
            sink.next(event(CONTEXT_COMPRESSED, summary));
        }

        // 3. 组装上下文(短期 + 中期注入 + 长期检索)
        List<Msg> context = memory.buildContext(userMsg);

        // 4. 思考:调用模型(流式)
        ModelResponse resp = model.call(context, toolkit.toFunctionSpecs())
            .doOnNext(chunk -> sink.next(toEvent(chunk)))
            .blockLast();

        // 5. 判断是否调用工具
        if (resp.hasToolCalls()) {
            for (ToolCallBlock call : resp.toolCalls()) {
                // 6. HITL 审批
                if (toolkit.needsApproval(call.name())) {
                    boolean ok = hitl.requestPermission(call, sink).block();
                    if (!ok) {
                        memory.addShortTerm(deniedMsg(call));
                        continue;
                    }
                }
                // 7. 行动:执行工具(带重试)
                ToolResult result = retry.execute(
                    () -> toolkit.invoke(call)
                );
                // 8. 观察:结果写回上下文
                memory.addShortTerm(result.toMsg());
                sink.next(event(TOOL_RESULT_END, result));
            }
            continue;  // 继续思考
        }

        // 9. 无工具调用 → 最终回复
        memory.addShortTerm(resp.toMsg());
        break;
    }

    // 10. 记忆沉淀
    memory.consolidate(currentReply);
    sink.next(event(REPLY_END));
    sink.complete();
}
```

---

## 四、分层记忆生态

### 4.1 三层结构

```
┌───────────────────────────────────────────────┐
│  短期记忆 ShortTermMemory                      │
│  存储: JVM 内存 (RingBuffer, 最近 N 轮)       │
│  内容: 当前会话 Msg 原文                       │
│  作用: 组装模型上下文的主体                    │
│  淘汰: 超阈值 → 旧消息送入中期压缩             │
├───────────────────────────────────────────────┤
│  中期记忆 MidTermMemory                        │
│  存储: Redis (会话级 TTL)                      │
│  内容: 旧消息的 LLM 摘要 + 关键事实/决策点     │
│  作用: 压缩历史,保留要点,注入上下文            │
│  淘汰: 会话结束后选择性沉淀到长期               │
├───────────────────────────────────────────────┤
│  长期记忆 LongTermMemory                       │
│  存储: MySQL + 向量索引                        │
│  内容: 跨会话的用户偏好/项目知识/历史结论       │
│  作用: 语义检索 top-k,按需注入上下文            │
│  检索: embedding 相似度搜索                    │
└───────────────────────────────────────────────┘
```

### 4.2 核心接口

```java
public interface MemoryManager {
    /** 写入短期记忆 */
    void addShortTerm(Msg msg);

    /** 组装上下文: 短期原文 + 中期摘要注入 + 长期检索注入 */
    List<Msg> buildContext(Msg currentQuery);

    /** 会话结束沉淀: 中期 → 长期 */
    void consolidate(Msg finalReply);

    /** 语义检索长期记忆 */
    List<Msg> retrieve(String query, int topK);

    /** 获取指定会话全部历史(持久化恢复) */
    List<Msg> loadHistory(String sessionId);
}
```

### 4.3 上下文压缩策略

```java
public class ContextCompactor {
    int tokenThreshold = 8000;   // 触发压缩的 token 阈值
    int keepRecentRounds = 6;    // 压缩时保留最近 K 轮原文

    boolean shouldCompress(MemoryManager memory) {
        return memory.estimateTokens() > tokenThreshold;
    }

    Summary compress(MemoryManager memory) {
        // 1. 取出超出 keepRecentRounds 的旧消息
        List<Msg> oldMsgs = memory.takeOldMessages(keepRecentRounds);
        // 2. 调 LLM 生成结构化摘要
        Summary summary = model.summarize(oldMsgs);
        // 3. 摘要存入中期记忆(Redis)
        memory.midTerm().store(summary);
        // 4. 原始旧消息可归档到 MySQL
        return summary;
    }
}
```

### 4.4 存储设计

**MySQL 表:**

```sql
-- 会话表
CREATE TABLE agent_session (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     VARCHAR(64),
    title       VARCHAR(256),
    status      VARCHAR(32),   -- RUNNING / SUSPENDED / ENDED
    created_at  DATETIME,
    updated_at  DATETIME
);

-- 消息持久化表(长期存储 + 恢复)
CREATE TABLE agent_message (
    id          VARCHAR(64) PRIMARY KEY,
    session_id  VARCHAR(64),
    role        VARCHAR(16),
    name        VARCHAR(64),
    content     JSON,          -- ContentBlock 列表
    metadata    JSON,
    created_at  DATETIME,
    INDEX idx_session (session_id, created_at)
);

-- 长期记忆表(向量化)
CREATE TABLE agent_memory_long (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     VARCHAR(64),
    session_id  VARCHAR(64),
    content     TEXT,
    embedding   JSON,          -- 向量(存 JSON 数组;或用 sqlite-vec/pgvector)
    metadata    JSON,
    created_at  DATETIME,
    INDEX idx_user (user_id)
);
```

**Redis Key 设计:**

```
session:{sessionId}:midterm     → 中期记忆摘要 (Hash/String, TTL 24h)
session:{sessionId}:state       → 会话运行状态 (String)
session:{sessionId}:pending     → 待审批/待回答的 HITL 请求 (Hash)
user:{userId}:warm              → 预热的长期记忆缓存 (Set)
```

> 向量检索说明: 本地部署默认用 MySQL 存储 embedding(JSON 数组),检索时全量计算余弦相似度(适合中小规模);后续可切换到 sqlite-vec 或独立向量库。

---

## 五、工具生态

### 5.1 工具抽象

```java
public interface Tool {
    String name();
    String description();
    JsonNode schema();                          // JSON Schema 参数定义
    ToolResult invoke(JsonNode input, ToolContext ctx);
}

public record ToolContext(
    String sessionId,
    String workDir,                             // 工作目录
    int timeoutSeconds,
    Map<String, String> env
) {}

public record ToolResult(
    String id,
    boolean success,
    String output,
    Map<String, Object> metadata
) {
    public Msg toMsg() { ... }
}
```

### 5.2 内置工具清单

| 工具 | 类名 | 功能 | 默认需审批 |
|---|---|---|---|
| Bash | `BashTool` | 执行 shell 命令,超时控制 | 是 |
| 读文件 | `ReadTool` | 读取文件内容,支持行范围 | 否 |
| 写文件 | `WriteTool` | 写入文件 | 是 |
| 编辑文件 | `EditTool` | 字符串替换编辑 | 是 |
| Grep | `GrepTool` | ripgrep 搜索封装 | 否 |
| Glob | `GlobTool` | 文件名匹配 | 否 |
| 脚本 | `ScriptTool` | 运行 Python/Node 脚本 | 是 |

### 5.3 Toolkit 工具箱

```java
public class Toolkit {
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Set<String> approvalRequired = ConcurrentHashMap.newKeySet();

    public void register(Tool tool) { ... }
    public void markApprovalRequired(String toolName) { ... }
    public boolean needsApproval(String toolName) { ... }

    /** 转换为 OpenAI Function Calling 格式 */
    public List<FunctionDef> toFunctionSpecs() {
        return tools.values().stream().map(t -> new FunctionDef(
            t.name(), t.description(), t.schema()
        )).toList();
    }

    public ToolResult invoke(ToolCallBlock call) {
        Tool tool = tools.get(call.name());
        JsonNode input = parseJson(call.input());
        return tool.invoke(input, currentContext());
    }
}
```

### 5.4 Function Calling

模型调用时,工具列表以 OpenAI function 格式传入:

```json
{
  "model": "gpt-4o",
  "messages": [...],
  "tools": [{
    "type": "function",
    "function": {
      "name": "bash",
      "description": "执行 shell 命令",
      "parameters": {
        "type": "object",
        "properties": {
          "command": {"type": "string", "description": "要执行的命令"}
        },
        "required": ["command"]
      }
    }
  }],
  "tool_choice": "auto"
}
```

模型返回 `tool_calls` 时,引擎解析并执行,结果以 `tool` role 消息写回。

### 5.5 MCP 接入

MCP (Model Context Protocol) 是外部工具服务器协议,通过 JSON-RPC 通信。

```java
public class McpClient {
    // 传输方式: stdio 或 SSE
    private final McpTransport transport;

    /** 拉取 MCP Server 暴露的工具列表 */
    public List<Tool> listTools() {
        JsonNode resp = transport.request("tools/list", Map.of());
        return parseTools(resp);
    }

    /** 调用 MCP 工具 */
    public ToolResult callTool(String name, JsonNode args) {
        JsonNode resp = transport.request("tools/call",
            Map.of("name", name, "arguments", args));
        return parseResult(resp);
    }
}
```

配置示例:

```yaml
agent:
  mcp:
    servers:
      - name: filesystem
        transport: stdio
        command: npx
        args: ["-y", "@modelcontextprotocol/server-filesystem", "/workspace"]
      - name: github
        transport: sse
        url: http://localhost:3001/sse
```

启动时遍历配置,连接每个 MCP Server,拉取工具注册进 Toolkit。

---

## 六、Skill 体系

### 6.1 渐进式披露模型

```
┌────────────────────────────────────────────────┐
│ Level 0: 元数据 (始终加载)                     │
│   name / description / whenToUse               │
│   注入 system prompt,让模型知道有哪些技能       │
├────────────────────────────────────────────────┤
│ Level 1: 指令 (按需加载)                       │
│   完整 SKILL.md 指令文档                       │
│   模型判断需要时调用 load_skill(name) 触发加载  │
├────────────────────────────────────────────────┤
│ Level 2: 工具与资源 (执行时加载)               │
│   该 Skill 专属的工具集和资源文件               │
│   实际执行时才注入到 Toolkit                   │
└────────────────────────────────────────────────┘
```

### 6.2 Skill 结构

```java
public class Skill {
    private String name;
    private String description;
    private String whenToUse;            // L0: 告诉模型何时使用
    private String instructions;         // L1: 完整指令文档
    private List<Tool> tools;            // L2: 专属工具
    private Map<String, String> resources; // L2: 资源文件
    private boolean instructionsLoaded;
    private boolean toolsLoaded;
}

public record SkillMeta(
    String name, String description, String whenToUse
) {}
```

### 6.3 SkillRegistry

```java
public class SkillRegistry {
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    /** 注册 Skill */
    public void register(Skill skill) { ... }

    /** L0: 获取所有技能元数据,注入 system prompt */
    public List<SkillMeta> listMeta() {
        return skills.values().stream()
            .map(s -> new SkillMeta(s.getName(), s.getDescription(), s.getWhenToUse()))
            .toList();
    }

    /** L1+L2: 按需加载完整 Skill */
    public Skill load(String name) {
        Skill s = skills.get(name);
        s.loadInstructions();   // 读取 SKILL.md
        s.loadTools();          // 实例化专属工具
        return s;
    }

    /** 多技能组合 */
    public List<Skill> compose(List<String> names) {
        return names.stream().map(this::load).toList();
    }
}
```

### 6.4 Skill 目录约定

```
resources/skills/
├── code-review/
│   ├── SKILL.md              # 指令文档(L1)
│   └── tools.yml             # 工具定义(L2)
├── data-analysis/
│   ├── SKILL.md
│   └── tools.yml
└── document-writing/
    ├── SKILL.md
    └── tools.yml
```

### 6.5 内置 LoadSkill 工具

引擎注册一个特殊的 `load_skill` 工具,模型可调用来按需加载技能:

```java
public class LoadSkillTool implements Tool {
    public String name() { return "load_skill"; }
    public String description() {
        return "按需加载一个技能的完整指令和工具。可用技能: "
             + skillRegistry.listMeta();
    }
    public ToolResult invoke(JsonNode input, ToolContext ctx) {
        Skill skill = skillRegistry.load(input.get("name").asText());
        return new ToolResult(id, true,
            "已加载技能: " + skill.getName() + "\n" + skill.getInstructions());
    }
}
```

---

## 七、Human-in-the-Loop

### 7.1 三种暂停场景

| 场景 | 触发 | 恢复方式 |
|---|---|---|
| 工具审批 | 调用需审批工具(如 Bash) | 前端回复 approve/deny |
| 主动提问 | Agent 需要用户提供信息 | 前端回复答案 |
| 用户暂停 | 用户主动点击暂停 | 用户点击恢复 |

### 7.2 实现机制

基于 `CompletableFuture` + 持久化,Agent 线程 park 等待,WebSocket 回调恢复。

```java
public class HitlManager {
    // 待审批: toolCallId → Future
    private final Map<String, CompletableFuture<Boolean>> pendingApprovals = new ConcurrentHashMap<>();
    // 待回答: questionId → Future
    private final Map<String, CompletableFuture<String>> pendingQuestions = new ConcurrentHashMap<>();

    /** 请求工具审批(暂停 Agent 线程) */
    public Mono<Boolean> requestPermission(ToolCallBlock call, FluxSink<AgentEvent> sink) {
        var future = new CompletableFuture<Boolean>();
        pendingApprovals.put(call.id(), future);

        // 推送审批请求事件给前端
        sink.next(AgentEvent.of(
            EventType.HITL_PERMISSION_REQUEST,
            call.id(), call
        ));

        // 持久化会话状态为 SUSPENDED
        sessionManager.updateState(call.sessionId(), SessionState.SUSPENDED);

        return Mono.fromFuture(future)
            .doFinally(sig -> {
                pendingApprovals.remove(call.id());
                sessionManager.updateState(call.sessionId(), SessionState.RUNNING);
            });
    }

    /** 前端回调:审批结果 */
    public void resumeApproval(String toolCallId, boolean approved) {
        var future = pendingApprovals.get(toolCallId);
        if (future != null) future.complete(approved);
    }

    /** 主动提问(暂停 Agent 线程) */
    public Mono<String> askUser(String question, FluxSink<AgentEvent> sink) { ... }

    /** 前端回调:用户回答 */
    public void resumeAnswer(String questionId, String answer) { ... }
}
```

### 7.3 断点续传

会话状态持久化到 MySQL + Redis:

```
SUSPENDED 状态 + pending HITL 请求 → 服务重启后可恢复
  → 从 MySQL 加载会话历史
  → 重新创建 CompletableFuture
  → 等待前端回调
```

---

## 八、长任务架构

### 8.1 治理能力矩阵

| 治理点 | 实现类 | 说明 |
|---|---|---|
| 上下文压缩 | `ContextCompactor` | token/轮数阈值触发,LLM 摘要旧消息 |
| 异常重试 | `RetryPolicy` | 指数退避,工具/模型调用失败重试,最大 3 次 |
| 预算控制 | `BudgetControl` | 单 reply token 上限,超额强制收尾 |
| 超时熔断 | `TimeoutGuard` | 单工具/单轮/总时长三级超时 |
| 断点续传 | `SessionManager` | 会话状态持久化,重启恢复 |

### 8.2 RetryPolicy

```java
public class RetryPolicy {
    int maxRetries = 3;
    long initialBackoffMs = 1000;
    double multiplier = 2.0;

    public <T> T execute(Supplier<T> action) {
        int attempt = 0;
        long backoff = initialBackoffMs;
        while (true) {
            try {
                return action.get();
            } catch (Exception e) {
                if (++attempt > maxRetries) throw e;
                Thread.sleep(backoff);
                backoff *= multiplier;
            }
        }
    }
}
```

### 8.3 BudgetControl

```java
public class BudgetControl {
    int maxTokensPerReply = 100000;

    void check(MemoryManager memory) {
        if (memory.estimateTokens() > maxTokensPerReply) {
            // 注入 HintBlock,提醒模型立即产出最终结论
            memory.addHint("预算即将耗尽,请立即总结并给出最终回复。");
            // 强制下一轮 tool_choice=none
            forceNoTools = true;
        }
    }
}
```

---

## 九、模型接入

### 9.1 统一接口

```java
public interface ModelAdapter {
    /** 流式调用 */
    Flux<ModelChunk> stream(List<Msg> context, List<FunctionDef> tools);

    /** 阻塞调用 */
    Mono<ModelResponse> call(List<Msg> context, List<FunctionDef> tools);

    /** 生成 embedding(长期记忆向量化) */
    Mono<float[]> embed(String text);
}
```

### 9.2 OpenAI 兼容实现

```java
public class OpenAICompatibleAdapter implements ModelAdapter {
    private final String baseUrl;     // 用户自定义
    private final String apiKey;
    private final String model;
    private final WebClient webClient;

    public Flux<ModelChunk> stream(List<Msg> context, List<FunctionDef> tools) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", toOpenAIMessages(context),
            "tools", toOpenAITools(tools),
            "tool_choice", "auto",
            "stream", true
        );
        return webClient.post()
            .uri(baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)           // SSE 流
            .map(this::parseChunk);
    }
}
```

### 9.3 配置

```yaml
agent:
  model:
    base-url: ${MODEL_BASE_URL:https://api.openai.com/v1}
    api-key: ${MODEL_API_KEY:}
    model: ${MODEL_NAME:gpt-4o}
    embedding-model: ${EMBEDDING_MODEL:text-embedding-3-small}
    timeout: 60s
```

---

## 十、响应式流与 WebSocket

### 10.1 全链路 Flux

```
用户消息
  → WebSocketHandler 接收
  → ReActAgent.replyStream(msg)           返回 Flux<AgentEvent>
    → ModelAdapter.stream()               内部 Flux<ModelChunk>
      → map(AgentEvent)                   转事件
      → EventBus.publish()                总线分发
    → ToolResult 产生                      事件
  → WebSocketSink.push(event)             推前端
  → 前端实时渲染
```

### 10.2 WebSocket 端点

```java
public class AgentWebSocketHandler implements WebSocketHandler {
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 输入: 前端发来的消息
        Flux<WebSocketMessage> input = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .map(this::parseClientMessage);

        // 输出: Agent 事件流 → JSON
        Flux<WebSocketMessage> output = input.flatMap(msg ->
            routeMessage(msg, session)
                .map(AgentEvent::toJson)
                .map(session::textMessage)
        );

        return session.send(output);
    }
}
```

### 10.3 通信协议

**前端 → 后端:**

```json
{"type": "user_message", "sessionId": "xxx", "content": "你好"}
{"type": "hitl_resume", "approvalId": "xxx", "approved": true}
{"type": "hitl_answer", "questionId": "xxx", "answer": "使用方案A"}
{"type": "pause", "sessionId": "xxx"}
{"type": "abort", "sessionId": "xxx"}
```

**后端 → 前端:**

```json
{"type": "reply_start", "replyId": "xxx", "sessionId": "xxx"}
{"type": "thinking_block_delta", "replyId": "xxx", "delta": "思考中..."}
{"type": "text_block_delta", "replyId": "xxx", "delta": "你好"}
{"type": "tool_call_start", "replyId": "xxx", "toolCallId": "xxx", "name": "bash", "input": "..."}
{"type": "hitl_permission_request", "replyId": "xxx", "toolCallId": "xxx", "name": "bash", "input": "..."}
{"type": "tool_result_end", "replyId": "xxx", "toolCallId": "xxx", "output": "..."}
{"type": "context_compressed", "replyId": "xxx", "summary": "..."}
{"type": "reply_end", "replyId": "xxx"}
{"type": "error", "message": "..."}
```

---

## 十一、配置体系总览

```yaml
# application.yml

server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/react_agent?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}

agent:
  model:
    base-url: ${MODEL_BASE_URL:https://api.openai.com/v1}
    api-key: ${MODEL_API_KEY:}
    model: ${MODEL_NAME:gpt-4o}
    embedding-model: ${EMBEDDING_MODEL:text-embedding-3-small}
    timeout: 60s
  react:
    max-iters: 25
  memory:
    short-term-cap: 20
    compress-threshold: 8000
    long-term:
      enabled: true
  tools:
    approval-required: [bash, write, edit, script]
  hitl:
    timeout: 300s
  budget:
    max-tokens-per-reply: 100000
  mcp:
    servers: []
  websocket:
    path: /ws/agent
  skill:
    base-dir: classpath:skills/
```

---

## 十二、数据库初始化

完整 SQL 脚本见 `react-agent-server/src/main/resources/schema.sql`,核心表:

- `agent_session` — 会话
- `agent_message` — 消息持久化
- `agent_memory_long` — 长期记忆(含 embedding)
- `agent_skill_registry` — 已注册 Skill 元数据
- `agent_tool_call_log` — 工具调用审计日志

---

## 十三、非功能性设计

| 维度 | 方案 |
|---|---|
| 并发 | JDK 21 虚拟线程,单实例支撑高并发 WebSocket |
| 可观测 | 结构化日志 + Actuator + 指标(工具调用次数/耗时/token) |
| 安全 | 工具审批 + 工作目录隔离 + 命令黑名单 |
| 扩展 | 模块化,每个接口可独立实现替换 |
| 配置 | application.yml + 环境变量,无需改代码切换模型/存储 |

---

> 本文档是技术蓝图。详细的分步操作执行计划见 `EXECUTION_PLAN.md`。
