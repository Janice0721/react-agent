package com.reactagent.model;

import com.reactagent.core.msg.Msg;
import com.reactagent.core.skill.SkillMeta;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 模型适配器统一接口。
 * <p>
 * 不同模型供应商实现此接口,当前默认实现 {@link OpenAICompatibleAdapter}。
 * 支持流式(stream)、非流式(call)和向量化(embed)三种调用方式。
 */
public interface ModelAdapter {

    /**
     * 流式调用:返回事件流,前端可逐 token 订阅。
     *
     * @param context 对话上下文(包含 system / user / assistant / tool 消息)
     * @param tools   可用工具列表(转换为 OpenAI function calling 格式)
     * @return 流式响应块
     */
    Flux<ModelChunk> stream(List<Msg> context, List<FunctionDef> tools, List<SkillMeta> skills);

    /**
     * 非流式调用:阻塞等待完整响应。
     *
     * @param context 对话上下文
     * @param tools   可用工具列表
     * @return 完整响应
     */
    Mono<ModelResponse> call(List<Msg> context, List<FunctionDef> tools, List<SkillMeta> skills);

    /**
     * 文本向量化(用于长期记忆 embedding)。
     *
     * @param text 待向量化的文本
     * @return 向量(float 数组)
     */
    Mono<float[]> embed(String text);
}
