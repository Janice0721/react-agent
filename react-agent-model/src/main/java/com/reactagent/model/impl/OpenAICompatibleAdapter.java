package com.reactagent.model.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.Role;
import com.reactagent.core.msg.Usage;
import com.reactagent.core.msg.block.ContentBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.core.msg.block.ThinkingBlock;
import com.reactagent.core.msg.block.ToolCallBlock;
import com.reactagent.core.msg.block.ToolResultBlock;
import com.reactagent.core.skill.SkillMeta;
import com.reactagent.model.FunctionDef;
import com.reactagent.model.ModelAdapter;
import com.reactagent.model.ModelChunk;
import com.reactagent.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议适配器。
 * <p>
 * 适用于任何符合 OpenAI /v1/chat/completions 格式的服务:
 * OpenAI 官方、OneAPI、Dify、本地 vLLM、通义千问兼容模式等。
 * <p>
 * 用户只需配置 base-url / api-key / model 即可接入。
 */
public class OpenAICompatibleAdapter implements ModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleAdapter.class);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String embeddingModel;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAICompatibleAdapter(String baseUrl, String apiKey, String model,
                                   String embeddingModel) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ==================== 流式调用 ====================

    @Override
    public Flux<ModelChunk> stream(List<Msg> context, List<FunctionDef> tools, List<SkillMeta> skills) {
        ObjectNode body = buildRequestBody(context, tools, skills, true);
        String uri = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        return webClient.post()
                .uri(uri)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !line.equals("[DONE]"))
                .mapNotNull(this::parseStreamChunk)
                .onErrorResume(e -> {
                    log.error("流式调用失败", e);
                    ModelChunk err = ModelChunk.builder()
                            .textDelta("[ERROR] 模型调用失败: " + e.getMessage())
                            .finished(true)
                            .build();
                    return Flux.just(err);
                });
    }

    // ==================== 非流式调用 ====================

    @Override
    public Mono<ModelResponse> call(List<Msg> context, List<FunctionDef> tools, List<SkillMeta> skills) {
        ObjectNode body = buildRequestBody(context, tools, skills, false);
        String uri = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        return webClient.post()
                .uri(uri)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseCallResponse)
                .onErrorResume(e -> {
                    log.error("非流式调用失败", e);
                    ModelResponse resp = new ModelResponse();
                    resp.setText("[ERROR] 模型调用失败: " + e.getMessage());
                    resp.setFinishReason("error");
                    return Mono.just(resp);
                });
    }

    // ==================== 向量化 ====================

    @Override
    public Mono<float[]> embed(String text) {
        String uri = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", embeddingModel);
        body.put("input", text);

        return webClient.post()
                .uri(uri)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseEmbeddingResponse)
                .onErrorResume(e -> {
                    log.error("向量化失败", e);
                    return Mono.just(new float[0]);
                });
    }

    // ==================== 请求体构建 ====================

    /**
     * 构建 /v1/chat/completions 请求体
     */
    private ObjectNode buildRequestBody(List<Msg> context, List<FunctionDef> tools,
                                        List<SkillMeta> skills, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);

        // 消息列表
        ArrayNode messages = objectMapper.createArrayNode();
        for (Msg msg : context) {
            messages.add(toOpenAIMessage(msg));
        }
        body.set("messages", messages);

        // 工具列表(如果有)
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (FunctionDef tool : tools) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode fnNode = objectMapper.createObjectNode();
                fnNode.put("name", tool.getName());
                fnNode.put("description", tool.getDescription());
                fnNode.set("parameters",
                        tool.getParameters() != null ? tool.getParameters()
                                : objectMapper.createObjectNode());
                toolNode.set("function", fnNode);
                toolsArray.add(toolNode);
            }
            body.set("tools", toolsArray);
            body.put("tool_choice", "auto");
        }

        // 技能列表(如果有)
        if (skills != null && !skills.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("你可以使用以下技能,调用 load_skill 工具按需加载完整指令:\n");
            for (SkillMeta meta : skills) {
                sb.append("  - ").append(meta.getName())
                        .append(": ").append(meta.getDescription())
                        .append(" (使用场景: ").append(meta.getWhenToUse()).append(")\n");
            }
            sb.append("如果任务需要某个技能,先调用 load_skill 加载,再按指令执行。");
            body.put("skills", sb.toString());
        }
        log.warn("请求体: {}", body.toString());
        return body;
    }

    /**
     * 将内部 Msg 转为 OpenAI message 格式
     */
    private ObjectNode toOpenAIMessage(Msg msg) {
        ObjectNode msgNode = objectMapper.createObjectNode();

        // 映射 role
        String role = switch (msg.getRole()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
        };
        msgNode.put("role", role);
        if (msg.getName() != null && !msg.getName().isBlank()
                && msg.getRole() != Role.SYSTEM) {
            msgNode.put("name", msg.getName());
        }

        List<ContentBlock> blocks = msg.getContent();
        if (blocks == null || blocks.isEmpty()) {
            msgNode.put("content", "");
            return msgNode;
        }

        // 判断是否是工具结果消息
        ToolResultBlock toolResult = null;
        for (ContentBlock block : blocks) {
            if (block instanceof ToolResultBlock tr) {
                toolResult = tr;
                break;
            }
        }

        if (toolResult != null) {
            // 工具结果: role=tool, content=输出, tool_call_id=关联ID
            msgNode.put("role", "tool");
            msgNode.put("content", toolResult.getOutput() != null
                    ? toolResult.getOutput() : "");
            msgNode.put("tool_call_id", toolResult.getId() != null
                    ? toolResult.getId() : "");
            return msgNode;
        }

        // 普通消息:拼接文本
        StringBuilder textContent = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock tb) {
                textContent.append(tb.getText());
            }
        }
        msgNode.put("content", textContent.toString());

        // 如果是 assistant 且有工具调用,加 tool_calls
        if (msg.getRole() == Role.ASSISTANT) {
            List<ToolCallBlock> calls = msg.getToolCalls();
            if (!calls.isEmpty()) {
                ArrayNode toolCallsNode = objectMapper.createArrayNode();
                for (ToolCallBlock call : calls) {
                    ObjectNode callNode = objectMapper.createObjectNode();
                    callNode.put("id", call.getId() != null ? call.getId() : "");
                    callNode.put("type", "function");
                    ObjectNode fnNode = objectMapper.createObjectNode();
                    fnNode.put("name", call.getName() != null ? call.getName() : "");
                    fnNode.put("arguments", call.getInput() != null ? call.getInput() : "{}");
                    callNode.set("function", fnNode);
                    toolCallsNode.add(callNode);
                }
                msgNode.set("tool_calls", toolCallsNode);
            }
        }

        return msgNode;
    }

    // ==================== 响应解析 ====================

    /**
     * 解析 SSE 流式响应的单行
     */
    private ModelChunk parseStreamChunk(String line) {
        try {
            // 去掉 "data: " 前缀
            String json = line.trim();
            if (json.startsWith("data:")) {
                json = json.substring(5).trim();
            }
            if (json.isEmpty() || json.equals("[DONE]")) {
                return null;
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                return null;
            }

            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            String finishReason = choice.has("finish_reason") && !choice.path("finish_reason").isNull()
                    ? choice.get("finish_reason").asText() : null;

            ModelChunk.ModelChunkBuilder builder = ModelChunk.builder();

            // 解析文本增量
            if (delta.has("content") && !delta.get("content").isNull()) {
                builder.textDelta(delta.get("content").asText());
            }

            // 解析思考增量(部分兼容服务支持 reasoning_content 字段)
            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                builder.thinkingDelta(delta.get("reasoning_content").asText());
            }

            // 解析工具调用增量
            if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                JsonNode tcArray = delta.get("tool_calls");
                for (JsonNode tc : tcArray) {
                    int idx = tc.has("index") ? tc.get("index").asInt() : 0;
                    if (tc.has("id")) {
                        builder.toolCallId(tc.get("id").asText());
                        builder.toolCallIndex(idx);
                    }
                    JsonNode fn = tc.path("function");
                    if (fn.has("name")) {
                        builder.toolCallName(fn.get("name").asText());
                        builder.toolCallIndex(idx);
                    }
                    if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                        builder.toolCallArgsDelta(fn.get("arguments").asText());
                        builder.toolCallIndex(idx);
                    }
                }
            }

            // 解析 usage(最后一个 chunk 可能携带)
            JsonNode usageNode = root.path("usage");
            if (!usageNode.isMissingNode()) {
                builder.promptTokens(usageNode.path("prompt_tokens").asInt(0));
                builder.completionTokens(usageNode.path("completion_tokens").asInt(0));
                builder.totalTokens(usageNode.path("total_tokens").asInt(0));
            }

            if (finishReason != null) {
                builder.finished(true);
            }

            return builder.build();

        } catch (Exception e) {
            log.warn("解析流式响应失败, line={}", line, e);
            return null;
        }
    }

    /**
     * 解析非流式完整响应
     */
    private ModelResponse parseCallResponse(String json) {
        ModelResponse response = new ModelResponse();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                response.setText("[ERROR] 响应无 choices");
                return response;
            }

            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            response.setFinishReason(choice.has("finish_reason")
                    ? choice.get("finish_reason").asText() : "stop");

            // 文本内容
            if (message.has("content") && !message.get("content").isNull()) {
                response.setText(message.get("content").asText());
            }

            // 思考内容
            if (message.has("reasoning_content") && !message.get("reasoning_content").isNull()) {
                response.setThinking(message.get("reasoning_content").asText());
            }

            // 工具调用
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                List<ModelResponse.ToolCallInfo> calls = new ArrayList<>();
                for (JsonNode tc : toolCalls) {
                    ModelResponse.ToolCallInfo info = new ModelResponse.ToolCallInfo();
                    info.setId(tc.path("id").asText(""));
                    JsonNode fn = tc.path("function");
                    info.setName(fn.path("name").asText(""));
                    info.setArguments(fn.path("arguments").asText("{}"));
                    calls.add(info);
                }
                response.setToolCalls(calls);
            }

            // usage
            JsonNode usageNode = root.path("usage");
            if (!usageNode.isMissingNode()) {
                Usage usage = new Usage();
                usage.setPromptTokens(usageNode.path("prompt_tokens").asInt(0));
                usage.setCompletionTokens(usageNode.path("completion_tokens").asInt(0));
                usage.setTotalTokens(usageNode.path("total_tokens").asInt(0));
                response.setUsage(usage);
            }

        } catch (Exception e) {
            log.error("解析非流式响应失败", e);
            response.setText("[ERROR] 解析响应失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 解析 embedding 响应
     */
    private float[] parseEmbeddingResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return new float[0];
            }
            JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray()) {
                return new float[0];
            }
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = (float) embedding.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            log.error("解析 embedding 响应失败", e);
            return new float[0];
        }
    }
}
