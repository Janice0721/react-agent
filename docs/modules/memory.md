# react-agent-memory 分层记忆

> 模块路径: `react-agent-memory/`  
> 包名: `com.reactagent.memory`  
> 状态: ✅ 已实现

## 职责

提供三层记忆体系，支持上下文组装、记忆沉淀和上下文压缩。ReAct 引擎通过 `MemoryManager` 接口统一访问，不直接接触底层存储。后续可整体替换为中心化记忆服务。

## 三层架构

```
┌───────────────────────────────────────────────────────┐
│  MemoryManager (总管,引擎唯一入口)                     │
├───────────────────────────────────────────────────────┤
│                                                       │
│  ┌─────────────────┐  buildContext() 注入             │
│  │ 短期记忆 ShortTerm│  最近对话原文                    │
│  │ MySQL 落库       │  按会话维度持久化                  │
│  │ agent_message 表 │  重启可恢复                       │
│  └─────────────────┘                                  │
│                                                       │
│  ┌─────────────────┐  buildContext() 注入             │
│  │ 中期记忆 MidTerm │  会话级摘要                       │
│  │ Redis 存储       │  旧消息 LLM 压缩后存此             │
│  │ TTL 24h          │  超阈值自动触发压缩               │
│  └─────────────────┘                                  │
│                                                       │
│  ┌─────────────────┐  buildContext() 语义检索注入      │
│  │ 长期记忆 LongTerm│  跨会话知识                       │
│  │ Qdrant 向量库    │  embed + 余弦相似度 top-k         │
│  │ 按 userId 隔离   │  consolidate() 沉淀               │
│  └─────────────────┘                                  │
│                                                       │
└───────────────────────────────────────────────────────┘
```

## 包结构

```
com.reactagent.memory
├── api/                           # 接口层(可扩展)
│   ├── MemoryManager.java         # 总管接口
│   ├── ShortTermMemory.java       # 短期记忆接口
│   ├── MidTermMemory.java         # 中期记忆接口
│   └── LongTermMemory.java        # 长期记忆接口
├── model/                         # 数据模型
│   ├── MemorySummary.java         # 摘要模型
│   └── MemorySearchResult.java    # 检索结果模型
├── entity/                        # 持久化实体
│   ├── MessageEntity.java         # 消息实体(对应 agent_message 表)
│   └── MessageRepository.java     # JDBC Repository
├── shortterm/                     # 短期记忆实现
│   ├── ShortTermMemoryImpl.java   # MySQL 实现
│   └── MsgSerializer.java         # Msg ↔ Entity 序列化
├── midterm/                       # 中期记忆实现
│   └── MidTermMemoryImpl.java     # Redis 实现
├── longterm/                      # 长期记忆实现
│   └── QdrantLongTermMemory.java  # Qdrant 向量实现
├── config/                        # 配置
│   ├── MemoryProperties.java      # 配置属性绑定
│   └── MemoryConfig.java          # Spring 配置(QdrantClient Bean)
├── MemoryManagerImpl.java         # 总管实现(组合三层)
└── resources/
    └── schema.sql                 # 建表脚本
```

## 接口说明

### MemoryManager（总管，引擎唯一入口）

| 方法 | 说明 |
|---|---|
| `addShortTerm(Msg)` | 写入短期记忆（消息落库 MySQL） |
| `buildContext(sessionId, userId, query)` | 组装上下文：中期摘要 + 长期检索 + 短期原文 |
| `loadHistory(sessionId)` | 加载会话全部历史（用于恢复） |
| `estimateTokens(sessionId)` | 估算当前会话 token 数 |
| `consolidate(sessionId, userId, finalReply)` | 会话结束沉淀：LLM 提取关键信息 → 存 Qdrant |
| `clearSession(sessionId, userId)` | 清除会话全部记忆（短/中/长） |
| `compressContext(sessionId)` | 上下文压缩：token 超阈值 → 旧消息 LLM 摘要 → 存 Redis |

### ShortTermMemory（短期记忆）

| 方法 | 说明 |
|---|---|
| `add(Msg)` | 添加消息（落库） |
| `getRecent(sessionId, limit)` | 获取最近 N 条 |
| `getAll(sessionId)` | 获取全部 |
| `count(sessionId)` | 消息总数 |
| `estimateTokens(sessionId)` | token 估算（字符数/4） |
| `takeOldMessages(sessionId, keepRecent)` | 取出旧消息用于压缩 |
| `clear(sessionId)` | 清除会话消息 |

### MidTermMemory（中期记忆）

| 方法 | 说明 |
|---|---|
| `store(sessionId, summary)` | 存储摘要到 Redis |
| `get(sessionId)` | 获取全部摘要 |
| `getSummaryText(sessionId)` | 获取摘要拼接文本（注入上下文用） |
| `clear(sessionId)` | 清除会话摘要 |

### LongTermMemory（长期记忆）

| 方法 | 说明 |
|---|---|
| `store(Msg, userId)` | 存储记忆（自动 embedding → Qdrant） |
| `search(query, userId, topK)` | 语义检索 top-k |
| `delete(memoryId)` | 删除指定记忆 |
| `clear(userId)` | 清除用户全部记忆 |

## 存储设计

### MySQL 表（短期记忆）

```sql
CREATE TABLE agent_message (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    role            VARCHAR(16) NOT NULL,      -- USER/ASSISTANT/SYSTEM
    name            VARCHAR(64),
    content         JSON,                      -- ContentBlock 列表
    metadata        JSON,
    created_at      VARCHAR(32) NOT NULL,
    finished_at     VARCHAR(32),
    prompt_tokens   INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens    INT DEFAULT 0,
    INDEX idx_session_created (session_id, created_at)
);
```

### Redis Key（中期记忆）

```
session:{sessionId}:midterm  →  摘要列表 JSON  (TTL 24h)
```

### Qdrant Collection（长期记忆）

```
Collection: react_agent_memory
向量维度: 1536 (与 embedding 模型一致)
距离: Cosine
Payload: content / userId / sessionId / createdAt / msgId
检索过滤: 按 userId 隔离
```

## 配置

在 server 的 `application.yml` 中配置：

```yaml
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
      mode: always                          # 启动时自动建表
      schema-locations: classpath:schema.sql

agent:
  memory:
    short-term-limit: 20                    # 组装上下文取最近多少条
    compress-threshold: 8000                # token 超此值触发压缩
    compress-keep-recent: 6                 # 压缩保留最近几条原文
    long-term-top-k: 5                      # 长期记忆检索 top-k
    qdrant:
      host: ${QDRANT_HOST:localhost}
      port: ${QDRANT_PORT:6334}
      collection-name: react_agent_memory
      vector-dimension: 1536                # 需与 embedding 模型一致
      enabled: ${QDRANT_ENABLED:true}       # Qdrant 未启动时设 false 降级
```

## 如何使用

### 1. 前置准备

启动 MySQL、Redis、Qdrant 三个服务：

```bash
# MySQL（已在 SETUP.md 中安装）
brew services start mysql

# Redis
brew services start redis

# Qdrant（用 Docker 启动最简单）
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 \
  -v $(pwd)/qdrant_data:/qdrant/storage \
  qdrant/qdrant
```

### 2. 在代码中使用 MemoryManager

```java
@Service
public class MyService {

    @Autowired
    private MemoryManager memoryManager;

    public void handleUserMessage(String sessionId, String userId, String text) {
        // 1. 写入短期记忆（自动落库 MySQL）
        Msg userMsg = Msg.user(sessionId, "user", text);
        memoryManager.addShortTerm(userMsg);

        // 2. 组装上下文（短期原文 + 中期摘要 + 长期检索）
        List<Msg> context = memoryManager.buildContext(sessionId, userId, text);

        // 3. 调用模型...
        // List<Msg> context 传给 ModelAdapter.call(context, tools, skills)

        // 4. 模型回复也写入短期记忆
        Msg reply = Msg.assistant(sessionId, "agent", List.of(new TextBlock("回复内容")));
        memoryManager.addShortTerm(reply);

        // 5. 上下文过长时压缩（token 超阈值自动触发）
        //    旧消息 → LLM 摘要 → 存 Redis 中期记忆
        if (memoryManager.estimateTokens(sessionId) > 8000) {
            ((MemoryManagerImpl) memoryManager).compressContext(sessionId);
        }
    }

    public void onSessionEnd(String sessionId, String userId, Msg finalReply) {
        // 6. 会话结束沉淀长期记忆
        //    LLM 提取关键信息 → embedding → 存 Qdrant
        memoryManager.consolidate(sessionId, userId, finalReply);
    }
}
```

### 3. 上下文组装流程

`buildContext(sessionId, userId, query)` 返回的消息列表顺序：

```
1. [system] 中期记忆摘要（如果有，来自 Redis）
2. [user/hint] 长期记忆检索结果（如果有，来自 Qdrant 语义检索 top-5）
3. [user/assistant/...] 短期记忆原文（最近 20 条，来自 MySQL）
```

这个列表直接传给 `ModelAdapter.call(context, tools, skills)` 即可。

### 4. 记忆沉淀流程

会话结束时调用 `consolidate()`：

```
会话全部消息 → LLM 提取关键信息 → 每条关键信息 embedding → 存入 Qdrant
```

下次新会话时，`buildContext()` 会通过 `search(query, userId, 5)` 语义检索到相关记忆，注入上下文。

### 5. 降级模式

如果 Qdrant 未启动，设 `agent.memory.qdrant.enabled: false`：
- 短期记忆（MySQL）正常工作
- 中期记忆（Redis）正常工作
- 长期记忆（Qdrant）功能跳过，`buildContext` 不注入长期记忆
- `consolidate()` 静默跳过

## 可扩展性

三层记忆各有独立接口，实现类可替换：

| 层 | 当前实现 | 可替换为 |
|---|---|---|
| ShortTermMemory | MySQL (JDBC) | PostgreSQL / MongoDB / 内存缓存 |
| MidTermMemory | Redis | Caffeine / Memcached / 外部缓存服务 |
| LongTermMemory | Qdrant | Milvus / Pinecone / pgvector / Weaviate |

替换步骤：实现对应接口 → 在 `MemoryConfig` 中注册新 Bean → 无需改 ReAct 引擎代码。

后续演进为中心化记忆系统时，只需将 `MemoryManager` 实现改为 HTTP/gRPC 客户端，调用外部 Memory Service。

## 依赖

- react-agent-core
- react-agent-model（embedding 向量化）
- spring-boot-starter-jdbc（MySQL）
- spring-boot-starter-data-redis（Redis）
- mysql-connector-j
- io.qdrant:client（Qdrant）
- com.google.protobuf / com.google.guava（Qdrant 间接依赖）
