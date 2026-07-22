# react-agent-runtime ReAct 引擎

> 模块路径: `react-agent-runtime/`  
> 包名: `com.reactagent.runtime`  
> 状态: ⏳ 待实现

## 职责

ReAct 核心循环(思考-行动-观察) + 事件总线 + 上下文压缩 + 重试 + 预算控制。

## 待实现类
- `ReActAgent` — Agent 门面,组合所有组件
- `ReActLoop` — 核心循环控制器
- `EventBus` — 响应式事件总线 (Reactor Sinks)
- `ContextCompactor` — 上下文压缩器
- `RetryPolicy` — 指数退避重试
- `BudgetControl` — Token 预算控制

## ReAct 循环
1. 写入短期记忆
2. 压缩检查
3. 组装上下文
4. 模型调用(流式)
5. 有工具调用 → 审批 → 执行(重试) → 观察写回 → continue
6. 无工具调用 → 输出最终回复
7. 记忆沉淀

## 依赖(计划)
- react-agent-core / model / memory / tools / skills / hitl
- reactor-core
