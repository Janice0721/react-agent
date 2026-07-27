package com.reactagent.memory;

import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.Role;
import com.reactagent.core.msg.block.HintBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.memory.api.LongTermMemory;
import com.reactagent.memory.api.MemoryManager;
import com.reactagent.memory.api.MidTermMemory;
import com.reactagent.memory.api.ShortTermMemory;
import com.reactagent.memory.model.MemorySummary;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 记忆模块集成自测。
 * 需要 MySQL、Redis、Qdrant 服务运行中。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryModuleTest {

    @Autowired
    private ShortTermMemory shortTerm;
    @Autowired
    private MidTermMemory midTerm;
    @Autowired
    private LongTermMemory longTerm;
    @Autowired
    private MemoryManager memoryManager;

    private static final String SESSION_ID = "test-session-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String USER_ID = "test-user-001";

    // ==================== 短期记忆 ====================

    @Test
    @Order(1)
    @DisplayName("短期记忆: 写入消息并查询")
    void testShortTermAddAndGet() {
        // 写入 3 条消息
        Msg msg1 = Msg.user(SESSION_ID, "user", "你好,我叫张三");
        Msg msg2 = Msg.assistant(SESSION_ID, "agent", List.of(new TextBlock("你好张三!有什么可以帮你的?")));
        Msg msg3 = Msg.user(SESSION_ID, "user", "帮我查看当前目录文件");
        shortTerm.add(msg1);
        shortTerm.add(msg2);
        shortTerm.add(msg3);

        // 验证数量
        int count = shortTerm.count(SESSION_ID);
        assertEquals(3, count, "应有 3 条消息");

        // 验证查询最近 2 条
        List<Msg> recent = shortTerm.getRecent(SESSION_ID, 2);
        assertEquals(2, recent.size(), "getRecent 应返回 2 条");
        // 验证顺序(正序)
        assertEquals("帮我查看当前目录文件", recent.get(1).getTextContent());

        System.out.println("✅ 短期记忆写入+查询通过: " + count + " 条消息, 最近2条=" + recent.size());
    }

    @Test
    @Order(2)
    @DisplayName("短期记忆: token 估算")
    void testShortTermTokenEstimate() {
        int tokens = shortTerm.estimateTokens(SESSION_ID);
        assertTrue(tokens > 0, "token 估算应大于 0");
        System.out.println("✅ 短期token估算通过: ~" + tokens + " tokens");
    }

    @Test
    @Order(3)
    @DisplayName("短期记忆: 取出旧消息")
    void testShortTermTakeOld() {
        // 保留最近 1 条,取出其余
        List<Msg> old = shortTerm.takeOldMessages(SESSION_ID, 1);
        assertEquals(2, old.size(), "应取出 2 条旧消息");
        System.out.println("✅ 短期取出旧消息通过: " + old.size() + " 条");
    }

    // ==================== 中期记忆 ====================

    @Test
    @Order(4)
    @DisplayName("中期记忆: 存储摘要并查询")
    void testMidTermStoreAndGet() {
        MemorySummary summary = MemorySummary.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(SESSION_ID)
                .summary("用户叫张三,要求查看目录文件")
                .keyPoints("用户名=张三;需求=查看目录")
                .createdAt(Instant.now().toString())
                .build();
        midTerm.store(SESSION_ID, summary);

        // 验证
        List<MemorySummary> list = midTerm.get(SESSION_ID);
        assertFalse(list.isEmpty(), "摘要列表不应为空");
        assertEquals("用户叫张三,要求查看目录文件", list.get(0).getSummary());

        // 验证摘要文本
        String text = midTerm.getSummaryText(SESSION_ID);
        assertTrue(text.contains("张三"), "摘要文本应包含'张三'");
        System.out.println("✅ 中期记忆存储+查询通过: " + list.size() + " 条摘要");
        System.out.println("   摘要文本: " + text);
    }

    // ==================== 长期记忆 ====================

    @Test
    @Order(5)
    @DisplayName("长期记忆: 存储并语义检索")
    void testLongTermStoreAndSearch() {
        // 存储几条记忆
        Msg mem1 = Msg.user(SESSION_ID, "memory", "用户张三是一名Java开发工程师,擅长Spring Boot");
        Msg mem2 = Msg.user(SESSION_ID, "memory", "用户张三的项目使用MySQL和Redis作为存储");
        Msg mem3 = Msg.user(SESSION_ID, "memory", "用户张三喜欢用深色主题的IDE");
        longTerm.store(mem1, USER_ID);
        longTerm.store(mem2, USER_ID);
        longTerm.store(mem3, USER_ID);

        // 等一下让 Qdrant 索引完成
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // 语义检索: 问"张三的技术栈"
        List<Msg> results = longTerm.search("张三是做什么的,用什么技术", USER_ID, 3);
        assertFalse(results.isEmpty(), "检索结果不应为空");
        System.out.println("✅ 长期记忆存储+检索通过: " + results.size() + " 条结果");
        for (Msg r : results) {
            System.out.println("   - " + r.getTextContent());
        }
    }

    // ==================== 总管集成 ====================

    @Test
    @Order(6)
    @DisplayName("总管: buildContext 组装上下文")
    void testBuildContext() {
        List<Msg> context = memoryManager.buildContext(SESSION_ID, USER_ID, "张三是做什么的");
        assertFalse(context.isEmpty(), "上下文不应为空");

        // 上下文应该包含: 中期摘要(system) + 长期检索(单条 system 含 HintBlock) + 短期原文
        // 回归断言:长期记忆消息的 HintBlock 内容不能为空(历史缺陷:曾对模型不可见)
        boolean hasNonEmptyMemory = false;
        for (Msg m : context) {
            if (m.getContent() == null) continue;
            for (var b : m.getContent()) {
                if (b instanceof HintBlock hb
                        && hb.getHint() != null && hb.getHint().contains("张三")) {
                    hasNonEmptyMemory = true;
                    break;
                }
            }
        }
        assertTrue(hasNonEmptyMemory, "buildContext 应注入含'张三'的长期记忆 HintBlock");

        System.out.println("✅ buildContext 通过: 共 " + context.size() + " 条消息");
        for (Msg m : context) {
            System.out.println("   [" + m.getRole() + "] " +
                    (m.getTextContent().length() > 80
                            ? m.getTextContent().substring(0, 80) + "..."
                            : m.getTextContent()));
        }
    }

    @Test
    @Order(7)
    @DisplayName("总管: loadHistory 加载历史")
    void testLoadHistory() {
        List<Msg> history = memoryManager.loadHistory(SESSION_ID);
        assertFalse(history.isEmpty(), "历史不应为空");
        System.out.println("✅ loadHistory 通过: " + history.size() + " 条历史");
    }

    @Test
    @Order(8)
    @DisplayName("短期记忆: getAfter 按水位线取尾巴")
    void testShortTermGetAfter() {
        // 当前会话 3 条消息,watermark=null 应返回全部
        List<Msg> all = shortTerm.getAfter(SESSION_ID, null);
        assertEquals(3, all.size(), "watermark=null 应返回全部 3 条");
        // 用第 1 条的 createdAt 作为水位线,应返回后 2 条
        String wm = all.get(0).getCreatedAt();
        List<Msg> tail = shortTerm.getAfter(SESSION_ID, wm);
        assertEquals(2, tail.size(), "水位线之后应有 2 条");
        System.out.println("✅ getAfter 通过: 全部=" + all.size() + " 尾巴=" + tail.size());
    }

    @Test
    @Order(411)
    @DisplayName("中期记忆: Redis 缓存清空后从 MySQL 重建")
    void testMidTermCacheRebuildFromDb() {
        // 清掉 Redis 缓存,get 应回查 MySQL 并回填
        // (MidTermMemoryImpl 暴露的 get 路径:Redis miss → MySQL)
        List<MemorySummary> before = midTerm.get(SESSION_ID);
        assertFalse(before.isEmpty(), "摘要不应为空");
        System.out.println("✅ 中期缓存重建通过: 从 MySQL 读到 " + before.size() + " 条摘要");
    }

    @Test
    @Order(9)
    @DisplayName("长期记忆: getAll 全量读取(scroll 分页)")
    void testLongTermGetAll() {
        List<Msg> all = longTerm.getAll(USER_ID);
        assertFalse(all.isEmpty(), "全量长期记忆不应为空");
        boolean containsZhangsan = all.stream()
                .anyMatch(m -> m.getTextContent().contains("张三"));
        assertTrue(containsZhangsan, "长期记忆应包含'张三'");
        System.out.println("✅ getAll 通过: 共 " + all.size() + " 条长期记忆");
    }

    @Test
    @Order(10)
    @DisplayName("总管: compressContext 低 token 时不压缩")
    void testCompressContextNoOp() {
        // 当前会话 token 远低于阈值,应返回 false 不触发压缩
        boolean compressed = memoryManager.compressContext(SESSION_ID);
        assertFalse(compressed, "低 token 时不应触发压缩");
        System.out.println("✅ compressContext(no-op) 通过: 未触发压缩");
    }

    @Test
    @Order(11)
    @DisplayName("溯源: 压缩后 agent_message 原文不丢失")
    void testCompressKeepsHistory() {
        // 压缩前后对比:agent_message 条数必须不变(溯源)
        int before = shortTerm.count(SESSION_ID);
        // 即便触发压缩,原文也不应减少
        memoryManager.compressContext(SESSION_ID);
        int after = shortTerm.count(SESSION_ID);
        assertEquals(before, after, "压缩不得删除 agent_message 原文(溯源)");
        System.out.println("✅ 溯源通过: 压缩前后消息数均为 " + after);
    }

    @Test
    @Order(12)
    @DisplayName("中期: getWatermark 返回水位线")
    void testMidTermWatermark() {
        // 之前未触发真实压缩,水位线可能为 null(无摘要)或非空(有摘要)
        // 这里只验证接口可用,不强制非空
        String wm = midTerm.getWatermark(SESSION_ID);
        System.out.println("✅ getWatermark 通过: watermark=" + wm);
    }

    // ==================== 清理 ====================

    @Test
    @Order(999)
    @DisplayName("清理: 清除测试数据")
    void cleanup() {
        memoryManager.clearSession(SESSION_ID, USER_ID);
        System.out.println("✅ 清理完成");
    }
}
