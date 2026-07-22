package com.reactagent.model;

import com.reactagent.model.impl.OpenAICompatibleAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型适配层配置。
 * <p>
 * 从 application.yml 读取配置,创建 {@link ModelAdapter} Bean。
 * 默认实现 {@link OpenAICompatibleAdapter}。
 */
@Configuration
public class ModelConfig {
    @Autowired
    ModelProperties properties;

    @Bean
    public ModelAdapter modelAdapter() {
        return new OpenAICompatibleAdapter(
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getModel(),
                properties.getEmbeddingModel()
        );
    }
}
