# react-agent-model 模型适配层

> 模块路径: `react-agent-model/`  
> 包名: `com.reactagent.model`

## 职责

封装大模型调用,提供统一的流式/非流式/向量化接口。  
当前实现 OpenAI 兼容协议,覆盖 OpenAI 官方、OneAPI、本地 vLLM 等服务。

## 类结构

```
com.reactagent.model
├── ModelAdapter.java               # 统一接口
├── ModelProperties.java            # 配置属性绑定 (agent.model.*)
├── ModelConfig.java                # Spring 配置类
├── FunctionDef.java                # 函数定义 DTO
├── ModelChunk.java                 # 流式响应增量块
├── ModelResponse.java              # 非流式完整响应
└── impl/
    └── OpenAICompatibleAdapter.java # OpenAI 兼容实现 (410 行)
```

## ModelAdapter 接口

```java
Flux<ModelChunk> stream(List<Msg> context, List<FunctionDef> tools);  // 流式
Mono<ModelResponse> call(List<Msg> context, List<FunctionDef> tools); // 非流式
Mono<float[]> embed(String text);                                      // 向量化
```

## OpenAICompatibleAdapter

- 端点: `{base-url}/chat/completions` 和 `{base-url}/embeddings`
- 流式: SSE 解析,提取 text/reasoning_content/tool_calls 增量
- 非流式: 完整 JSON 解析,提取 text/toolCalls/usage/finishReason
- 消息转换: Msg → OpenAI message (支持 user/assistant/system/tool)
- 工具转换: FunctionDef → OpenAI tools 数组
- 容错: 所有方法 onErrorResume,失败返回错误文本

## 配置

```yaml
agent:
  model:
    base-url: ${MODEL_BASE_URL:https://api.openai.com/v1}
    api-key: ${MODEL_API_KEY:}
    model: ${MODEL_NAME:gpt-4o}
    embedding-model: ${EMBEDDING_MODEL:text-embedding-3-small}
    timeout: 60
```

## 依赖
- react-agent-core
- spring-boot-starter-webflux (WebClient)
- spring-boot-starter
