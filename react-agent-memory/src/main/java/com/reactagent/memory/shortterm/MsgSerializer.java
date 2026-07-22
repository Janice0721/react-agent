package com.reactagent.memory.shortterm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.Role;
import com.reactagent.core.msg.Usage;
import com.reactagent.core.msg.block.ContentBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.memory.entity.MessageEntity;

import java.util.List;
import java.util.Map;

/**
 * Msg 与 MessageEntity 之间的序列化/反序列化工具。
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
        try {
            List<ContentBlock> blocks = MAPPER.readValue(
                entity.getContentJson(),
                new TypeReference<List<ContentBlock>>() {}
            );
            // Jackson 反序列化 sealed interface 需要类型信息,这里用宽松回退
            msg.setContent(blocks);
        } catch (Exception e) {
            // 回退:把 contentJson 当纯文本处理
            TextBlock tb = new TextBlock();
            tb.setText(entity.getContentJson());
            msg.setContent(List.of(tb));
        }
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
