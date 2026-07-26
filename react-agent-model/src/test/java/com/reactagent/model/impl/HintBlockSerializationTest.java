package com.reactagent.model.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.Role;
import com.reactagent.core.msg.block.HintBlock;
import com.reactagent.core.msg.block.TextBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试:验证 HintBlock(长期记忆/提示注入)内容会被序列化进模型可见的 content。
 * <p>
 * 历史缺陷:适配器只序列化 TextBlock,导致 buildContext 注入的长期记忆对模型不可见。
 */
class HintBlockSerializationTest {

    @Test
    @DisplayName("HintBlock 内容应出现在序列化后的 content 中")
    void hintBlockShouldBeSerializedToContent() throws Exception {
        OpenAICompatibleAdapter adapter = new OpenAICompatibleAdapter(
                "http://localhost:0", "dummy-key", "dummy-model", "dummy-embed");

        // 构造一条含 HintBlock 的 system 消息(模拟 buildContext 的长期记忆注入)
        HintBlock hint = new HintBlock();
        hint.setHint("用户张三是一名Java开发工程师");
        hint.setSource("long_term_memory");

        Msg msg = new Msg();
        msg.setId("msg-1");
        msg.setSessionId("s1");
        msg.setRole(Role.SYSTEM);
        msg.setName("memory");
        msg.setContent(new ArrayList<>(List.of(hint)));
        msg.setCreatedAt("2026-07-27T00:00:00Z");

        // 反射调用 private buildRequestBody(纯函数,不触网)
        Method m = OpenAICompatibleAdapter.class.getDeclaredMethod(
                "buildRequestBody", List.class, List.class, List.class, boolean.class);
        m.setAccessible(true);
        ObjectNode body = (ObjectNode) m.invoke(adapter, List.of(msg), null, null, false);

        // 取 messages[0].content
        JsonNode messages = body.get("messages");
        assertTrue(messages.isArray() && messages.size() == 1, "应只有 1 条消息");
        String content = messages.get(0).get("content").asText("");

        // 回归断言:content 不能为空,且必须包含 hint 文本与来源标签
        assertFalse(content.isBlank(), "HintBlock 消息的 content 不应为空(历史缺陷:曾丢失)");
        assertTrue(content.contains("用户张三是一名Java开发工程师"),
                "content 应包含 hint 原文");
        assertTrue(content.contains("long_term_memory"), "content 应包含来源标签");
    }

    @Test
    @DisplayName("混合 TextBlock + HintBlock 都应序列化")
    void mixedBlocksShouldBothBeSerialized() throws Exception {
        OpenAICompatibleAdapter adapter = new OpenAICompatibleAdapter(
                "http://localhost:0", "dummy-key", "dummy-model", "dummy-embed");

        TextBlock text = new TextBlock("你好");
        HintBlock hint = new HintBlock("记得用户偏好深色主题", "long_term_memory");

        Msg msg = new Msg();
        msg.setSessionId("s1");
        msg.setRole(Role.USER);
        msg.setContent(new ArrayList<>(List.of(text, hint)));

        Method m = OpenAICompatibleAdapter.class.getDeclaredMethod(
                "buildRequestBody", List.class, List.class, List.class, boolean.class);
        m.setAccessible(true);
        ObjectNode body = (ObjectNode) m.invoke(adapter, List.of(msg), null, null, false);
        String content = body.get("messages").get(0).get("content").asText("");

        assertTrue(content.contains("你好"), "TextBlock 内容应在");
        assertTrue(content.contains("用户偏好深色主题"), "HintBlock 内容也应在");
    }
}
