# react-agent-core 核心领域模型

> 模块路径: `react-agent-core/`  
> 包名: `com.reactagent.core`

## 职责

定义整个引擎的核心领域模型和抽象接口,所有其他模块都依赖本模块。

## 包结构

```
com.reactagent.core
├── msg/                    # 消息与内容块
│   ├── Msg.java            # 对话消息(持久化和上下文管理的基本单位)
│   ├── Role.java           # 角色枚举: USER / ASSISTANT / SYSTEM
│   ├── Usage.java          # Token 用量统计
│   ├── block/              # 内容块(消息由多个块组成)
│   │   ├── ContentBlock.java       # 内容块接口
│   │   ├── TextBlock.java          # 文本块
│   │   ├── ThinkingBlock.java      # 思考/推理块
│   │   ├── ToolCallBlock.java      # 工具调用块
│   │   ├── ToolCallState.java      # 工具调用状态枚举
│   │   ├── ToolResultBlock.java    # 工具结果块
│   │   ├── ToolResultState.java    # 工具结果状态枚举
│   │   ├── DataBlock.java          # 多媒体数据块
│   │   └── HintBlock.java          # 提示块(注入摘要/记忆)
│   └── event/              # 事件(流式推送)
│       ├── AgentEvent.java         # 事件模型
│       └── EventType.java          # 25 种事件类型枚举
├── tool/                   # 工具抽象
│   ├── Tool.java           # 工具接口
│   ├── ToolResult.java     # 工具执行结果
│   └── ToolContext.java    # 工具执行上下文
└── skill/                  # 技能抽象
    ├── Skill.java          # 技能接口(L0/L1/L2 分层)
    └── SkillMeta.java      # 技能元数据(L0)
```

## 核心类说明

### Msg
对话的一轮,包含 id/sessionId/name/role/content/metadata/createdAt/finishedAt/usage。  
提供工厂方法: `Msg.user()`, `Msg.assistant()`, `Msg.system()`, `Msg.toolResult()`。  
辅助方法: `getTextContent()`, `getToolCalls()`, `hasToolCalls()`, `getThinkingContent()`。

### ContentBlock 体系
消息内容由多个内容块组成,6 种块类型覆盖文本、思考、工具调用、工具结果、多媒体、提示。

### AgentEvent + EventType
流式推送给前端的增量单位,涵盖生命周期/文本流/思考流/工具流/人机协同/上下文治理/会话控制/异常共 25 种事件类型。

### Tool 接口
```java
String name();              // 工具名称
String description();        // 工具描述
JsonNode schema();           // 参数 JSON Schema
ToolResult invoke(JsonNode input, ToolContext ctx);  // 执行
boolean approvalRequired();  // 是否需要用户审批
```

### Skill 接口
渐进式披露三层:
- L0: `name()` / `description()` / `whenToUse()` — 始终加载
- L1: `instructions()` / `loadInstructions()` — 按需加载
- L2: `tools()` / `loadTools()` — 执行时加载

## 依赖
- Lombok
- Jackson (jackson-databind)
- SLF4J
