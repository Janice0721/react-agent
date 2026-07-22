# react-agent-server 服务层

> 模块路径: `react-agent-server/`  
> 包名: `com.reactagent`

## 职责

Spring Boot 启动入口,提供 WebSocket 端点、REST 接口和前端 Demo 页面。

## 类结构

```
com.reactagent
├── ReactAgentApplication.java   # Spring Boot 启动类
└── (待实现)
    ├── ws/                       # WebSocket 端点
    ├── controller/               # REST 接口
    └── config/                   # 配置类
```

## 配置文件

`src/main/resources/application.yml`:
```yaml
server:
  port: 9999
agent:
  model:
    base-url: http://oneai.17usoft.com/v1
    api-key: sk-xxx
    model: deepseek-v4-pro
    embedding-model: text-embedding-v4
    timeout: 600
```

## 待实现
- WebSocketHandler: 接收前端消息,路由处理,推送事件
- SessionManager: 会话管理
- REST 接口: 非流式对话、会话管理

## 依赖
- spring-boot-starter-web
- spring-boot-configuration-processor
- react-agent-model (及间接依赖)
