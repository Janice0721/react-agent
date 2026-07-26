package com.reactagent.memory.longterm;

import com.reactagent.core.msg.Msg;
import com.reactagent.core.msg.block.ContentBlock;
import com.reactagent.core.msg.block.TextBlock;
import com.reactagent.memory.api.LongTermMemory;
import com.reactagent.model.ModelAdapter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.Match;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import io.qdrant.client.grpc.Points.WithVectorsSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 长期记忆实现:基于 Qdrant 向量数据库 + 语义检索。
 * <p>
 * 每条记忆:文本内容 + 向量 + 元数据(userId/sessionId/createdAt)。
 * 检索:embedding 查询 → 余弦相似度 → top-k,按 userId 过滤隔离。
 * <p>
 * 可扩展:后续可替换为其他向量库或中心化记忆服务。
 */
public class QdrantLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(QdrantLongTermMemory.class);

    private final QdrantClient qdrantClient;
    private final ModelAdapter modelAdapter;
    private final String collectionName;
    private final int vectorDimension;

    public QdrantLongTermMemory(QdrantClient qdrantClient, ModelAdapter modelAdapter,
                                String collectionName, int vectorDimension) {
        this.qdrantClient = qdrantClient;
        this.modelAdapter = modelAdapter;
        this.collectionName = collectionName;
        this.vectorDimension = vectorDimension;
    }

    /**
     * 初始化:确保 collection 存在且向量维度与配置一致。
     * 维度不一致(例如更换 embedding 模型)会删除并重建集合,
     * 避免后续 store 因维度不匹配而静默失败。
     */
    public void init() {
        try {
            List<String> collections = qdrantClient.listCollectionsAsync(java.time.Duration.ofSeconds(10)).get();
            if (!collections.contains(collectionName)) {
                createCollection();
                return;
            }
            // collection 存在,校验维度
            int existingDim = extractExistingDimension();
            if (existingDim > 0 && existingDim != vectorDimension) {
                log.warn("Qdrant collection 维度不匹配: existing={} expected={},删除并重建集合: {}",
                        existingDim, vectorDimension, collectionName);
                qdrantClient.deleteCollectionAsync(collectionName).get();
                createCollection();
            } else {
                log.info("Qdrant collection 已存在且维度匹配: {} 维度={}", collectionName,
                        existingDim > 0 ? existingDim : vectorDimension);
            }
        } catch (Exception e) {
            log.warn("Qdrant 初始化失败(如果 Qdrant 未启动会跳过): {}", e.getMessage());
        }
    }

    private void createCollection() throws Exception {
        qdrantClient.createCollectionAsync(collectionName,
                VectorParams.newBuilder()
                        .setSize(vectorDimension)
                        .setDistance(Distance.Cosine)
                        .build()
        ).get();
        log.info("Qdrant collection 已创建: {} 维度={}", collectionName, vectorDimension);
    }

    /** 读取已存在 collection 的向量维度,无法读取返回 -1。 */
    private int extractExistingDimension() {
        try {
            CollectionInfo info = qdrantClient.getCollectionInfoAsync(collectionName).get();
            VectorsConfig vc = info.getConfig().getParams().getVectorsConfig();
            if (vc.getConfigCase() == VectorsConfig.ConfigCase.PARAMS) {
                return (int) vc.getParams().getSize();
            }
        } catch (Exception e) {
            log.debug("提取现有维度失败: {}", e.getMessage());
        }
        return -1;
    }

    @Override
    public void store(Msg msg, String userId) {
        String content = toPlainText(msg);
        if (content.isBlank()) return;

        try {
            float[] vector = modelAdapter.embed(content).block();
            if (vector == null || vector.length == 0) {
                log.warn("向量化失败,跳过存储: msgId={}", msg.getId());
                return;
            }

            String pointId = UUID.randomUUID().toString();
            String createdAt = Instant.now().toString();
            String safeUserId = userId != null ? userId : "default";

            // 构建向量
            Vector.Builder vectorBuilder = Vector.newBuilder();
            for (float v : vector) {
                vectorBuilder.addData(v);
            }
            Vectors vectors = Vectors.newBuilder().setVector(vectorBuilder.build()).build();

            // 构建点
            PointStruct point = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(pointId).build())
                .setVectors(vectors)
                .putPayload("content", ValueFactory.value(content))
                .putPayload("userId", ValueFactory.value(safeUserId))
                .putPayload("sessionId", ValueFactory.value(
                        msg.getSessionId() != null ? msg.getSessionId() : ""))
                .putPayload("createdAt", ValueFactory.value(createdAt))
                .putPayload("msgId", ValueFactory.value(
                        msg.getId() != null ? msg.getId() : ""))
                .build();

            qdrantClient.upsertAsync(collectionName, List.of(point)).get();

            log.info("长期记忆存储: userId={} contentLen={}", safeUserId, content.length());
        } catch (Exception e) {
            log.error("长期记忆存储失败: msgId={} contentLen={} expectedDim={} error={}",
                    msg.getId(), content.length(), vectorDimension,
                    e.getMessage());
        }
    }

    @Override
    public List<Msg> search(String query, String userId, int topK) {
        try {
            float[] queryVector = modelAdapter.embed(query).block();
            if (queryVector == null || queryVector.length == 0) return List.of();

            String safeUserId = userId != null ? userId : "default";

            // 按 userId 过滤
            Filter filter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                        .setKey("userId")
                        .setMatch(Match.newBuilder().setKeyword(safeUserId).build())
                        .build())
                    .build())
                .build();

            // 搜索:vector 是 repeated float,用 addVector 逐个添加
            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .setFilter(filter)
                .setLimit(topK)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());
            for (float v : queryVector) {
                searchBuilder.addVector(v);
            }

            List<ScoredPoint> points = qdrantClient.searchAsync(searchBuilder.build()).get();

            List<Msg> results = new ArrayList<>();
            for (ScoredPoint point : points) {
                String content = extractPayload(point, "content");
                String sessionId = extractPayload(point, "sessionId");
                String createdAt = extractPayload(point, "createdAt");

                Msg msg = Msg.user(sessionId, "memory", content);
                msg.setId(point.getId().getUuid());
                msg.setCreatedAt(createdAt);
                results.add(msg);
            }

            log.info("长期记忆检索: userId={} query={} results={}", safeUserId, query, results.size());
            return results;
        } catch (Exception e) {
            log.error("长期记忆检索失败: query={}", query, e);
            return List.of();
        }
    }

    @Override
    public List<Msg> getAll(String userId) {
        List<Msg> results = new ArrayList<>();
        try {
            String safeUserId = userId != null ? userId : "default";
            Filter filter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                        .setKey("userId")
                        .setMatch(Match.newBuilder().setKeyword(safeUserId).build())
                        .build())
                    .build())
                .build();
            // 分页 scroll,避免一次性拉取过多
            PointId offset = null;
            int pageSize = 100;
            while (true) {
                ScrollPoints.Builder sp = ScrollPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setFilter(filter)
                    .setLimit(pageSize)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setWithVectors(WithVectorsSelector.newBuilder().setEnable(false).build());
                if (offset != null) sp.setOffset(offset);
                ScrollResponse resp = qdrantClient.scrollAsync(sp.build()).get();
                for (RetrievedPoint point : resp.getResultList()) {
                    String content = extractPayloadValue(point.getPayloadMap(), "content");
                    String sessionId = extractPayloadValue(point.getPayloadMap(), "sessionId");
                    String createdAt = extractPayloadValue(point.getPayloadMap(), "createdAt");
                    Msg msg = Msg.user(sessionId, "memory", content);
                    msg.setId(point.getId().getUuid());
                    msg.setCreatedAt(createdAt);
                    results.add(msg);
                }
                if (!resp.hasNextPageOffset()) break;
                offset = resp.getNextPageOffset();
            }
            log.info("长期记忆全量读取: userId={} results={}", safeUserId, results.size());
            return results;
        } catch (Exception e) {
            log.error("长期记忆全量读取失败: userId={}", userId, e);
            return results;
        }
    }

    @Override
    public void delete(String memoryId) {
        try {
            qdrantClient.deleteAsync(collectionName,
                    List.of(PointId.newBuilder().setUuid(memoryId).build())).get();
            log.info("长期记忆删除: id={}", memoryId);
        } catch (Exception e) {
            log.error("长期记忆删除失败: id={}", memoryId, e);
        }
    }

    @Override
    public void clear(String userId) {
        try {
            String safeUserId = userId != null ? userId : "default";
            Filter filter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                        .setKey("userId")
                        .setMatch(Match.newBuilder().setKeyword(safeUserId).build())
                        .build())
                    .build())
                .build();

            qdrantClient.deleteAsync(collectionName, filter).get();
            log.info("清除用户长期记忆: userId={}", safeUserId);
        } catch (Exception e) {
            log.error("清除长期记忆失败: userId={}", userId, e);
        }
    }

    // ==================== 工具方法 ====================

    private String toPlainText(Msg msg) {
        if (msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String extractPayload(ScoredPoint point, String key) {
        try {
            return point.getPayloadMap().get(key).getStringValue();
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 payload map 中提取字符串字段,兼容 ScoredPoint / RetrievedPoint。 */
    private String extractPayloadValue(
            java.util.Map<String, io.qdrant.client.grpc.JsonWithInt.Value> map, String key) {
        try {
            io.qdrant.client.grpc.JsonWithInt.Value v = map.get(key);
            return v != null ? v.getStringValue() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
