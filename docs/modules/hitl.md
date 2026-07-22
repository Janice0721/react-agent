# react-agent-hitl 人机协同

> 模块路径: `react-agent-hitl/`  
> 包名: `com.reactagent.hitl`  
> 状态: ⏳ 待实现

## 职责

提供工具审批、主动提问、暂停/恢复机制。

## 待实现类
- `HitlManager` — 管理审批/提问的 CompletableFuture
- `SessionManager` — 会话状态管理 (RUNNING/SUSPENDED/ABORTED/ENDED)
- `SessionState` — 会话状态枚举

## 机制
- 暂停: CompletableFuture 阻塞 Agent 线程
- 恢复: WebSocket 回调 resumeApproval/resumeAnswer
- 断点续传: 会话状态持久化,重启可恢复

## 依赖(计划)
- react-agent-core
- spring-boot-starter-data-redis
