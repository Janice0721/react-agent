package com.reactagent.memory;

import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.block.HintBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.memory.api.LongTermMemory;
import com.reactagent.memory.api.MemoryManager;
import com.reactagent.memory.api.MidTermMemory;
import com.reactagent.memory.api.ShortTermMemory;
import com.reactagent.memory.config.MemoryProperties;
import com.reactagent.memory.model.MemorySummary;
import com.reactagent.model.ModelAdapter;
import com.reactagent.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 记忆总管实现:组合三层记忆,提供统一上下文组装能力。
 * <p>
 * ReAct 引擎只依赖 {@link MemoryManager} 接口,不直接访问底层存储。
 * 后续可演进为中心化记忆服务(Memory Service)。
 */
@Component
public class MemoryManagerImpl implements MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryManagerImpl.class);

    private final ShortTermMemory shortTerm;
    private final MidTermMemory midTerm;
    private final LongTermMemory longTerm;
    private final ModelAdapter modelAdapter;
    private final MemoryProperties properties;

    public MemoryManagerImpl(ShortTermMemory shortTerm,
                             MidTermMemory midTerm,
                             LongTermMemory longTerm,
                             ModelAdapter modelAdapter,
                             MemoryProperties properties) {
        this.shortTerm = shortTerm;
        this.midTerm = midTerm;
        this.longTerm = longTerm;
        this.modelAdapter = modelAdapter;
        this.properties = properties;
    }

    @Override
    public void addShortTerm(Msg msg) {
        shortTerm.add(msg);
    }

    @Override
    public List<Msg> buildContext(String sessionId, String userId, String currentQuery) {
        List<Msg> context = new ArrayList<>();

        // 1. 注入中期记忆摘要(如果有)
        String summaryText = midTerm.getSummaryText(sessionId);
        if (summaryText != null && !summaryText.isBlank()) {
            Msg summaryMsg = Msg.system(sessionId, summaryText);
            context.add(summaryMsg);
        }

        // 2. 注入长期记忆检索结果(如果有查询)
        if (currentQuery != null && !currentQuery.isBlank()) {
            try {
                List<Msg> memories = longTerm.search(
                        currentQuery, userId, properties.getLongTermTopK());
                for (Msg mem : memories) {
                    HintBlock hint = new HintBlock();
                    hint.setHint(mem.getTextContent());
                    hint.setSource("long_term_memory");
                    Msg hintMsg = new Msg();
                    hintMsg.setId(UUID.randomUUID().toString());
                    hintMsg.setSessionId(sessionId);
                    hintMsg.setName("memory");
                    hintMsg.setRole(com.reactagent.core.msg.Role.USER);
                    hintMsg.setContent(new ArrayList<>(List.of(hint)));
                    hintMsg.setCreatedAt(Instant.now().toString());
                    context.add(hintMsg);
                }
                log.debug("长期记忆注入: {} 条", memories.size());
            } catch (Exception e) {
                log.warn("长期记忆检索失败,跳过: {}", e.getMessage());
            }
        }

        // 3. 短期记忆(最近 N 条原文)
        List<Msg> recent = shortTerm.getRecent(sessionId, properties.getShortTermLimit());
        context.addAll(recent);

        return context;
    }

    @Override
    public List<Msg> loadHistory(String sessionId) {
        return shortTerm.getAll(sessionId);
    }

    @Override
    public int estimateTokens(String sessionId) {
        return shortTerm.estimateTokens(sessionId);
    }

    /**
     * 记忆沉淀:将关键信息提取为长期记忆。
     * <p>
     * 1. 用 LLM 从会话中提取关键信息
     * 2. 存入长期记忆(Qdrant 向量化)
     */
    @Override
    public void consolidate(String sessionId, String userId, Msg finalReply) {
        try {
            // 获取会话全部消息
            List<Msg> allMsgs = shortTerm.getAll(sessionId);
            if (allMsgs.isEmpty()) {
                log.debug("会话无消息,跳过沉淀: {}", sessionId);
                return;
            }

            // 用 LLM 提取关键记忆
            String extractionPrompt = buildExtractionPrompt(allMsgs);
            Msg promptMsg = Msg.user(sessionId, "system",
                    "请从以下对话中提取值得长期记住的关键信息(用户偏好、重要事实、决策结论),"
                    + "用简洁的自然语言描述,每条一行。只提取真正重要的,忽略无关细节。\n\n"
                    + extractionPrompt);

            ModelResponse response = modelAdapter.call(
                    List.of(promptMsg), null, null).block();

            if (response != null && response.getText() != null && !response.getText().isBlank()) {
                // 将提取的记忆存入长期记忆
                String[] memories = response.getText().split("\n");
                for (String memory : memories) {
                    String trimmed = memory.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("-")) {
                        trimmed = trimmed.replaceAll("^[-*]\\s*", "");
                    }
                    if (!trimmed.isBlank()) {
                        Msg memMsg = Msg.user(sessionId, "system", trimmed);
                        longTerm.store(memMsg, userId);
                    }
                }
                log.info("记忆沉淀完成: session={} 提取 {} 条长期记忆", sessionId,
                        response.getText().split("\n").length);
            }
        } catch (Exception e) {
            log.error("记忆沉淀失败: session={}", sessionId, e);
        }
    }

    @Override
    public void clearSession(String sessionId, String userId) {
        shortTerm.clear(sessionId);
        midTerm.clear(sessionId);
        longTerm.clear(userId);
        log.info("清除会话全部记忆: session={}", sessionId);
    }

    /**
     * 上下文压缩:旧消息 → LLM 摘要 → 存入中期记忆。
     * 由 ContextCompactor 调用。
     */
    public void compressContext(String sessionId) {
        int tokens = shortTerm.estimateTokens(sessionId);
        if (tokens <= properties.getCompressThreshold()) {
            return;  // 未超阈值,不需要压缩
        }

        // 取出旧消息(保留最近 K 条)
        List<Msg> oldMsgs = shortTerm.takeOldMessages(
                sessionId, properties.getCompressKeepRecent());
        if (oldMsgs.isEmpty()) return;

        try {
            // 调 LLM 生成摘要
            String content = buildCompressionPrompt(oldMsgs);
            Msg promptMsg = Msg.user(sessionId, "system",
                    "请将以下对话历史压缩为简洁的摘要,保留关键事实、决策和上下文。"
                    + "用自然语言描述,不要遗漏重要信息。\n\n" + content);

            ModelResponse response = modelAdapter.call(
                    List.of(promptMsg), null, null).block();

            if (response != null && response.getText() != null) {
                MemorySummary summary = MemorySummary.builder()
                        .id(UUID.randomUUID().toString())
                        .sessionId(sessionId)
                        .summary(response.getText())
                        .fromTime(oldMsgs.get(0).getCreatedAt())
                        .toTime(oldMsgs.get(oldMsgs.size() - 1).getCreatedAt())
                        .createdAt(Instant.now().toString())
                        .build();
                midTerm.store(sessionId, summary);
                log.info("上下文压缩: session={} 旧消息={} 摘要已存入中期记忆",
                        sessionId, oldMsgs.size());
            }
        } catch (Exception e) {
            log.error("上下文压缩失败: session={}", sessionId, e);
        }
    }

    // ==================== 内部方法 ====================

    private String buildExtractionPrompt(List<Msg> msgs) {
        StringBuilder sb = new StringBuilder();
        for (Msg msg : msgs) {
            sb.append("[").append(msg.getRole()).append("] ");
            sb.append(msg.getTextContent()).append("\n");
        }
        return sb.toString();
    }

    private String buildCompressionPrompt(List<Msg> msgs) {
        StringBuilder sb = new StringBuilder();
        for (Msg msg : msgs) {
            sb.append("[").append(msg.getRole()).append("] ");
            sb.append(msg.getTextContent()).append("\n");
        }
        return sb.toString();
    }
}
