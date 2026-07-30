package com.middleware.manager.knowledge.store;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.middleware.manager.knowledge.config.AiConfig;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Milvus 2.5+ 向量存储：稠密向量 + BM25 稀疏向量双路，检索时由 Milvus 原生 RRF 融合。
 * <p>相比升级前的 2.3.4 单路稠密检索，解决两个问题：
 * <ul>
 *   <li>参数名、错误码这类稀有精确 token 会被稠密向量抹平，只有 BM25 召得回；
 *       而语义描述类查询又只有稠密向量能处理，两者必须互补</li>
 *   <li>正文此前塞在 metadata JSON 里受 VarChar(4096) 限制，切片一大就静默丢失；
 *       现在 text 是独立列（同时作为 BM25 的输入），metadata 只放过滤用的轻量字段</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.vector.type", havingValue = "milvus")
@Slf4j
public class MilvusVectorStore implements VectorStore {

    private static final String ID_FIELD = "id";
    private static final String VECTOR_FIELD = "vector";
    private static final String SPARSE_FIELD = "sparse";
    private static final String TEXT_FIELD = "text";
    private static final String META_FIELD = "metadata";
    private static final String BM25_FUNCTION = "text_bm25";

    private static final String SOURCE_FIELD = "source";
    private static final String SOURCE_TYPE_FIELD = "source_type";
    private static final String SOURCE_ID_FIELD = "source_id";
    private static final String CATEGORY_FIELD = "category";
    private static final String SOFTWARE_FIELD = "software";
    private static final String STATUS_FIELD = "status";

    private static final String CONTENT_KEY = "content";
    private static final int TEXT_MAX_LENGTH = 65535;
    private static final int META_MAX_LENGTH = 4096;
    private static final int SCALAR_MAX_LENGTH = 200;

    private static final Gson GSON = new Gson();

    private final AiConfig config;
    private MilvusClientV2 client;

    public MilvusVectorStore(AiConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://" + config.getVectorHost() + ":" + config.getVectorPort())
                .build());
        createCollection();
        log.info("Milvus 已连接 {}:{} collection={} 维度={} 分词器={}",
                config.getVectorHost(), config.getVectorPort(),
                config.getVectorCollection(), config.getVectorDimension(),
                config.getVectorAnalyzer());
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void createCollection() {
        String collection = config.getVectorCollection();
        if (client.hasCollection(HasCollectionReq.builder().collectionName(collection).build())) {
            verifyHybridSchema(collection);
            client.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
            return;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder().fieldName(ID_FIELD).dataType(DataType.VarChar)
                .maxLength(100).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName(VECTOR_FIELD).dataType(DataType.FloatVector)
                .dimension(config.getVectorDimension()).build());
        // enableAnalyzer 打开后 Milvus 才会为该列分词并驱动 BM25 Function。
        // 必须显式指定 analyzer：默认的 standard 分词器按非字母数字切分，
        // 中文没有空格会被当成一个巨大 token，BM25 那一路等于失效。
        // 该配置在 collection 创建后不可更改，要换只能重建 collection。
        Map<String, Object> analyzerParams = new HashMap<>();
        analyzerParams.put("type", config.getVectorAnalyzer());
        schema.addField(AddFieldReq.builder().fieldName(TEXT_FIELD).dataType(DataType.VarChar)
                .maxLength(TEXT_MAX_LENGTH)
                .enableAnalyzer(true)
                .analyzerParams(analyzerParams)
                .build());
        schema.addField(AddFieldReq.builder().fieldName(SPARSE_FIELD)
                .dataType(DataType.SparseFloatVector).build());
        schema.addField(AddFieldReq.builder().fieldName(META_FIELD).dataType(DataType.VarChar)
                .maxLength(META_MAX_LENGTH).build());
        for (String name : SCALAR_FIELDS) {
            schema.addField(AddFieldReq.builder().fieldName(name).dataType(DataType.VarChar)
                    .maxLength(SCALAR_MAX_LENGTH).build());
        }
        schema.addFunction(CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name(BM25_FUNCTION)
                .inputFieldNames(Collections.singletonList(TEXT_FIELD))
                .outputFieldNames(Collections.singletonList(SPARSE_FIELD))
                .build());

        List<IndexParam> indexes = new ArrayList<>();
        indexes.add(IndexParam.builder().fieldName(VECTOR_FIELD)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE).build());
        // BM25 度量要求 AUTOINDEX
        indexes.add(IndexParam.builder().fieldName(SPARSE_FIELD)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.BM25).build());

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .collectionSchema(schema)
                .indexParams(indexes)
                .build());
        log.info("Milvus collection '{}' 已创建（稠密 + BM25 稀疏双路）", collection);
    }

    /**
     * Milvus 2.3.4 建的旧 collection 没有 text / sparse 字段，混合检索会在运行时才失败。
     * 启动期显式检查并给出可执行的处置指引，好过线上检索时才暴露。
     */
    private void verifyHybridSchema(String collection) {
        List<String> fields = client.describeCollection(DescribeCollectionReq.builder()
                .collectionName(collection).build()).getFieldNames();
        if (fields != null && fields.contains(SPARSE_FIELD) && fields.contains(TEXT_FIELD)) {
            return;
        }
        throw new IllegalStateException(String.format(
                "Milvus collection '%s' 缺少 %s / %s 字段，是 2.5 之前的旧 schema，无法做混合检索。"
                        + "解析层与切片层已变更，向量本就需要全量重建：请先删除该 collection"
                        + "（或调用 recreateCollection），再重新导入文档。",
                collection, TEXT_FIELD, SPARSE_FIELD));
    }

    private static final List<String> SCALAR_FIELDS = Arrays.asList(
            SOURCE_FIELD, SOURCE_TYPE_FIELD, SOURCE_ID_FIELD,
            CATEGORY_FIELD, SOFTWARE_FIELD, STATUS_FIELD);

    @Override
    public void add(String id, float[] vector, Map<String, String> metadata) {
        addAll(Collections.singletonList(new VectorRecord(id, vector, metadata)));
    }

    @Override
    public void addAll(List<VectorRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        List<JsonObject> rows = records.stream()
                .map(this::toRow)
                .toList();
        client.upsert(UpsertReq.builder()
                .collectionName(config.getVectorCollection())
                .data(rows)
                .build());
    }

    private JsonObject toRow(VectorRecord record) {
        Map<String, String> meta = record.metadata() == null
                ? Collections.emptyMap()
                : record.metadata();
        // 正文搬到独立的 text 列：既作为 BM25 输入，也不再受 metadata 的 4096 字节限制
        String text = truncate(meta.getOrDefault(CONTENT_KEY, ""), TEXT_MAX_LENGTH);

        Map<String, String> rest = new LinkedHashMap<>(meta);
        rest.remove(CONTENT_KEY);

        JsonObject row = new JsonObject();
        row.addProperty(ID_FIELD, record.id());
        row.add(VECTOR_FIELD, GSON.toJsonTree(toBoxed(record.vector())));
        row.addProperty(TEXT_FIELD, text);
        row.addProperty(META_FIELD, fitMetadata(rest));
        for (String field : SCALAR_FIELDS) {
            row.addProperty(field, truncate(meta.getOrDefault(toMetaKey(field), ""), SCALAR_MAX_LENGTH));
        }
        return row;
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK) {
        return search(queryVector, topK, VectorSearchFilter.none());
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, VectorSearchFilter filter) {
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(config.getVectorCollection())
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .annsField(VECTOR_FIELD)
                .topK(topK)
                .outputFields(outputFields());
        String expr = buildExpr(filter);
        if (!expr.isEmpty()) {
            builder.filter(expr);
        }
        return toResults(client.search(builder.build()));
    }

    @Override
    public List<VectorSearchResult> hybridSearch(String queryText, float[] queryVector,
                                                 int topK, VectorSearchFilter filter) {
        if (queryText == null || queryText.isBlank()) {
            return search(queryVector, topK, filter);
        }
        String expr = buildExpr(filter);

        AnnSearchReq.AnnSearchReqBuilder<?, ?> dense = AnnSearchReq.builder()
                .vectorFieldName(VECTOR_FIELD)
                .vectors(Collections.<BaseVector>singletonList(new FloatVec(queryVector)))
                .topK(topK);
        AnnSearchReq.AnnSearchReqBuilder<?, ?> sparse = AnnSearchReq.builder()
                .vectorFieldName(SPARSE_FIELD)
                .vectors(Collections.<BaseVector>singletonList(new EmbeddedText(queryText)))
                .topK(topK);
        if (!expr.isEmpty()) {
            dense.expr(expr);
            sparse.expr(expr);
        }

        HybridSearchReq req = HybridSearchReq.builder()
                .collectionName(config.getVectorCollection())
                .searchRequests(Arrays.asList(dense.build(), sparse.build()))
                .ranker(new RRFRanker(config.getRrfK()))
                .topK(topK)
                .outFields(outputFields())
                .build();
        return toResults(client.hybridSearch(req));
    }

    @Override
    public void delete(String id) {
        client.delete(DeleteReq.builder()
                .collectionName(config.getVectorCollection())
                .ids(Collections.singletonList(id))
                .build());
    }

    @Override
    public void deleteBySource(String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) {
            return;
        }
        String filter = SOURCE_TYPE_FIELD + " == " + quote(sourceType)
                + " and " + SOURCE_ID_FIELD + " == " + quote(String.valueOf(sourceId));
        client.delete(DeleteReq.builder()
                .collectionName(config.getVectorCollection())
                .filter(filter)
                .build());
        log.debug("已按来源删除向量 {}", filter);
    }

    @Override
    public void deleteBySourceExcept(String sourceType, Long sourceId, Set<String> retainedIds) {
        if (sourceType == null || sourceId == null || retainedIds == null) {
            return;
        }
        if (retainedIds.isEmpty()) {
            deleteBySource(sourceType, sourceId);
            return;
        }
        String retainedIdExpression = retainedIds.stream()
                .map(MilvusVectorStore::quote)
                .collect(java.util.stream.Collectors.joining(","));
        String filter = SOURCE_TYPE_FIELD + " == " + quote(sourceType)
                + " and " + SOURCE_ID_FIELD + " == " + quote(String.valueOf(sourceId))
                + " and " + ID_FIELD + " not in [" + retainedIdExpression + "]";
        client.delete(DeleteReq.builder()
                .collectionName(config.getVectorCollection())
                .filter(filter)
                .build());
        log.debug("已清理来源的过期向量 sourceType={} sourceId={} retained={}",
                sourceType, sourceId, retainedIds.size());
    }

    @Override
    public long count() {
        GetCollectionStatsResp stats = client.getCollectionStats(GetCollectionStatsReq.builder()
                .collectionName(config.getVectorCollection())
                .build());
        return stats.getNumOfEntities() == null ? 0L : stats.getNumOfEntities();
    }

    /** 重建 collection：切换 embedding 模型或维度时使用，会清空全部向量。 */
    public void recreateCollection() {
        String collection = config.getVectorCollection();
        if (client.hasCollection(HasCollectionReq.builder().collectionName(collection).build())) {
            client.dropCollection(DropCollectionReq.builder().collectionName(collection).build());
            log.warn("Milvus collection '{}' 已删除，准备重建", collection);
        }
        createCollection();
    }

    // ---------- 结果转换 ----------

    private List<String> outputFields() {
        List<String> fields = new ArrayList<>(Arrays.asList(ID_FIELD, TEXT_FIELD, META_FIELD));
        fields.addAll(SCALAR_FIELDS);
        return fields;
    }

    private List<VectorSearchResult> toResults(SearchResp resp) {
        List<VectorSearchResult> results = new ArrayList<>();
        if (resp == null || resp.getSearchResults() == null) {
            return results;
        }
        for (List<SearchResp.SearchResult> group : resp.getSearchResults()) {
            for (SearchResp.SearchResult hit : group) {
                results.add(toResult(hit));
            }
        }
        return results;
    }

    private VectorSearchResult toResult(SearchResp.SearchResult hit) {
        Map<String, Object> entity = hit.getEntity();
        Map<String, String> meta = parseMetadata(entity);
        // 正文从独立列取回后放回 metadata，保持对调用方的契约不变
        Object text = entity == null ? null : entity.get(TEXT_FIELD);
        if (text != null) {
            meta.put(CONTENT_KEY, String.valueOf(text));
        }
        for (String field : SCALAR_FIELDS) {
            Object value = entity == null ? null : entity.get(field);
            if (value != null && !String.valueOf(value).isEmpty()) {
                meta.putIfAbsent(toMetaKey(field), String.valueOf(value));
            }
        }
        String id = hit.getId() == null ? null : String.valueOf(hit.getId());
        float score = hit.getScore() == null ? 0f : hit.getScore();
        return new VectorSearchResult(id, score, meta);
    }

    private Map<String, String> parseMetadata(Map<String, Object> entity) {
        Object raw = entity == null ? null : entity.get(META_FIELD);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> parsed = GSON.fromJson(String.valueOf(raw),
                    new TypeToken<Map<String, String>>() {}.getType());
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("metadata 解析失败，按空处理: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    // ---------- 过滤表达式 ----------

    static String buildExpr(VectorSearchFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>();
        appendIn(clauses, SOURCE_FIELD, filter.getSources());
        appendIn(clauses, SOURCE_TYPE_FIELD, filter.getSourceTypes());
        appendIn(clauses, SOURCE_ID_FIELD, filter.getSourceIds());
        appendIn(clauses, CATEGORY_FIELD, filter.getCategories());
        appendIn(clauses, SOFTWARE_FIELD, filter.getSoftware());
        appendIn(clauses, STATUS_FIELD, filter.getStatuses());
        return String.join(" and ", clauses);
    }

    private static void appendIn(List<String> clauses, String field, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        List<String> quoted = values.stream().map(MilvusVectorStore::quote).toList();
        clauses.add(field + " in [" + String.join(", ", quoted) + "]");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- 工具 ----------

    /** metadata 只放过滤用的轻量字段，正文已移到 text 列；仍做一次兜底压缩防止极端超限。 */
    static String fitMetadata(Map<String, String> metadata) {
        String json = GSON.toJson(metadata);
        if (byteLength(json) <= META_MAX_LENGTH) {
            return json;
        }
        Map<String, String> trimmed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            trimmed.put(entry.getKey(), truncate(entry.getValue(), 120));
            if (byteLength(GSON.toJson(trimmed)) > META_MAX_LENGTH) {
                trimmed.remove(entry.getKey());
                break;
            }
        }
        log.warn("metadata 超出 {} 字节上限，已裁剪字段", META_MAX_LENGTH);
        return GSON.toJson(trimmed);
    }

    /** 按 UTF-8 字节数截断，避免中文把 VarChar 撑爆（一个汉字 3 字节）。 */
    static String truncate(String value, int maxBytes) {
        if (value == null) {
            return "";
        }
        if (byteLength(value) <= maxBytes) {
            return value;
        }
        int end = value.length();
        while (end > 0 && byteLength(value.substring(0, end)) > maxBytes) {
            end--;
        }
        return value.substring(0, end);
    }

    private static int byteLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Milvus 列名用下划线，metadata 里是驼峰。 */
    private static String toMetaKey(String field) {
        return switch (field) {
            case SOURCE_TYPE_FIELD -> "sourceType";
            case SOURCE_ID_FIELD -> "sourceId";
            default -> field;
        };
    }

    private static List<Float> toBoxed(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}
