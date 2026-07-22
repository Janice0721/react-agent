# react-agent-memory 分层记忆

> 模块路径: `react-agent-memory/`  
> 包名: `com.reactagent.memory`  
> 状态: ⏳ 待实现

## 职责

提供三层记忆:
- **短期记忆**: JVM 内存 RingBuffer,保存最近 N 轮消息原文
- **中期记忆**: Redis 存储压缩摘要,会话级 TTL
- **长期记忆**: MySQL + 向量检索,跨会话持久化

## 待实现类
- `MemoryManager` 接口
- `ShortTermMemory` — 短期记忆
- `MidTermMemory` — 中期记忆 (Redis)
- `LongTermMemory` — 长期记忆 (MySQL + embedding)
- `MemoryManagerImpl` — 组合三层

## 设计要点
- `buildContext()`: 短期原文 + 中期摘要 + 长期检索注入
- `consolidate()`: 会话结束沉淀到长期
- 压缩策略: token 超阈值 → 旧消息 LLM 摘要 → 存中期

## 依赖(计划)
- react-agent-core
- react-agent-model (embedding)
- spring-boot-starter-data-redis
- spring-boot-starter-jdbc
- mysql-connector-j
