package com.reactagent.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型配置属性,从 application.yml 中 agent.model 前缀读取。
 * <p>
 * 配置示例:
 * <pre>
 * agent:
 *   model:
 *     base-url: https://api.openai.com/v1
 *     api-key: sk-xxx
 *     model: gpt-4o
 *     embedding-model: text-embedding-3-small
 *     timeout: 60s
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.model")
public class ModelProperties {

    /** 模型服务基础地址 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 默认对话模型 */
    private String model;

    /** 向量化模型 */
    private String embeddingModel;

    /** 调用超时(秒) */
    private int timeout;
}
