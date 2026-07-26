package com.reactagent.memory.shortterm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.Role;
import com.reactagent.core.msg.Usage;
import com.reactagent.core.msg.block.ContentBlock;
import com.reactagent.core.msg.block.DataBlock;
import com.reactagent.core.msg.block.HintBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.core.msg.block.ThinkingBlock;
import com.reactagent.core.msg.block.ToolCallBlock;
import com.reactagent.core.msg.block.ToolCallState;
import com.reactagent.core.msg.block.ToolResultBlock;
import com.reactagent.core.msg.block.ToolResultState;
import com.reactagent.memory.entity.MessageEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Msg 与 MessageEntity 之间的序列化/反序列化工具。
 * <p>
 * 由于 ContentBlock 是接口,Jackson 默认无法反序列化。
 * 这里用手动解析方式,根据字段名判断块类型。
 */
public class MsgSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Msg → MessageEntity(用于落库) */
    public static MessageEntity toEntity(Msg msg) {
        MessageEntity entity = new MessageEntity();
        entity.setId(msg.getId());
        entity.setSessionId(msg.getSessionId());
        entity.setRole(msg.getRole() != null ? msg.getRole().name() : "USER");
        entity.setName(msg.getName());
        try {
            entity.setContentJson(MAPPER.writeValueAsString(msg.getContent()));
            entity.setMetadataJson(msg.getMetadata() != null
                    ? MAPPER.writeValueAsString(msg.getMetadata()) : "{}");
        } catch (Exception e) {
            entity.setContentJson("[]");
            entity.setMetadataJson("{}");
        }
        entity.setCreatedAt(msg.getCreatedAt());
        entity.setFinishedAt(msg.getFinishedAt());
        if (msg.getUsage() != null) {
            entity.setPromptTokens(msg.getUsage().getPromptTokens());
            entity.setCompletionTokens(msg.getUsage().getCompletionTokens());
            entity.setTotalTokens(msg.getUsage().getTotalTokens());
        }
        return entity;
    }

    /** MessageEntity → Msg(从库中恢复) */
    public static Msg toMsg(MessageEntity entity) {
        Msg msg = new Msg();
        msg.setId(entity.getId());
        msg.setSessionId(entity.getSessionId());
        msg.setRole(Role.valueOf(entity.getRole()));
        msg.setName(entity.getName());
        msg.setContent(parseContentBlocks(entity.getContentJson()));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = MAPPER.readValue(entity.getMetadataJson(), Map.class);
            msg.setMetadata(meta);
        } catch (Exception e) {
            msg.setMetadata(Map.of());
        }
        msg.setCreatedAt(entity.getCreatedAt());
        msg.setFinishedAt(entity.getFinishedAt());
        if (entity.getTotalTokens() != null && entity.getTotalTokens() > 0) {
            Usage usage = new Usage();
            usage.setPromptTokens(entity.getPromptTokens() != null ? entity.getPromptTokens() : 0);
            usage.setCompletionTokens(entity.getCompletionTokens() != null ? entity.getCompletionTokens() : 0);
            usage.setTotalTokens(entity.getTotalTokens());
            msg.setUsage(usage);
        }
        return msg;
    }

    /**
     * 手动解析 ContentBlock JSON 列表。
     * 根据字段名判断块类型,兼容无 @JsonTypeInfo 的旧数据。
     */
    private static List<ContentBlock> parseContentBlocks(String json) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (json == null || json.isBlank()) return blocks;

        try {
            JsonNode array = MAPPER.readTree(json);
            if (!array.isArray()) return blocks;

            for (JsonNode node : array) {
                ContentBlock block = parseSingleBlock(node);
                if (block != null) blocks.add(block);
            }
        } catch (Exception e) {
            // 解析失败,作为纯文本
            TextBlock tb = new TextBlock();
            tb.setText(json);
            blocks.add(tb);
        }
        return blocks;
    }

    private static ContentBlock parseSingleBlock(JsonNode node) {
        if (node.has("text")) {
            TextBlock tb = new TextBlock();
            tb.setText(node.get("text").asText());
            return tb;
        }
        if (node.has("thinking")) {
            ThinkingBlock tb = new ThinkingBlock();
            tb.setThinking(node.get("thinking").asText());
            return tb;
        }
        if (node.has("hint")) {
            HintBlock hb = new HintBlock();
            hb.setHint(node.get("hint").asText());
            hb.setSource(node.has("source") ? node.get("source").asText() : null);
            return hb;
        }
        if (node.has("id") && node.has("name") && node.has("output")) {
            ToolResultBlock tr = new ToolResultBlock();
            tr.setId(node.get("id").asText());
            tr.setName(node.get("name").asText());
            tr.setOutput(node.get("output").asText());
            if (node.has("state")) {
                tr.setState(ToolResultState.valueOf(node.get("state").asText()));
            }
            return tr;
        }
        if (node.has("id") && node.has("name") && node.has("input")) {
            ToolCallBlock tc = new ToolCallBlock();
            tc.setId(node.get("id").asText());
            tc.setName(node.get("name").asText());
            tc.setInput(node.get("input").asText());
            if (node.has("state")) {
                tc.setState(ToolCallState.valueOf(node.get("state").asText()));
            }
            return tc;
        }
        if (node.has("mediaType") || node.has("data")) {
            DataBlock db = new DataBlock();
            db.setMediaType(node.has("mediaType") ? node.get("mediaType").asText() : null);
            db.setData(node.has("data") ? node.get("data").asText() : null);
            return db;
        }
        // 未知类型,尝试取 text 字段或 toString
        TextBlock tb = new TextBlock();
        tb.setText(node.toString());
        return tb;
    }

    /** 序列化 Msg 的文本内容(用于向量化) */
    public static String toPlainText(Msg msg) {
        if (msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText()).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
