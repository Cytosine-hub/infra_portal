package com.middleware.manager.knowledge.store;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.grpc.MutationResult;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.middleware.manager.knowledge.config.AiConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "app.vector.type", havingValue = "milvus")
@Slf4j
public class MilvusVectorStore implements VectorStore {
    private static final int VECTOR_DIM = 1024;
    private static final String ID_FIELD = "id";
    private static final String VECTOR_FIELD = "vector";
    private static final String META_FIELD = "metadata";
    private static final String SOURCE_FIELD = "source";
    private static final String SOURCE_TYPE_FIELD = "source_type";
    private static final String SOURCE_ID_FIELD = "source_id";
    private static final String CATEGORY_FIELD = "category";
    private static final String SOFTWARE_FIELD = "software";
    private static final String STATUS_FIELD = "status";

    private final AiConfig config;
    private final Gson gson = new Gson();
    private MilvusServiceClient client;
    private volatile boolean scalarInsertSupported = true;
    private volatile boolean scalarSearchSupported = true;

    public MilvusVectorStore(AiConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            client = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(config.getVectorHost())
                            .withPort(config.getVectorPort())
                            .build()
            );
            createCollection();
            createIndex();
            log.info("Milvus connected at {}:{}", config.getVectorHost(), config.getVectorPort());
        } catch (Exception e) {
            log.error("Failed to connect to Milvus: {}", e.getMessage());
            throw e;
        }
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

        R<Boolean> has = client.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collection).build()
        );
        if (has.getData() != null && has.getData()) {
            log.info("Milvus collection '{}' already exists", collection);
            return;
        }

        List<FieldType> fields = new ArrayList<>();
        fields.add(FieldType.newBuilder()
                .withName(ID_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(100)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build());
        fields.add(FieldType.newBuilder()
                .withName(VECTOR_FIELD)
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build());
        fields.add(FieldType.newBuilder()
                .withName(META_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(4096)
                .build());
        fields.add(varcharField(SOURCE_FIELD, 40));
        fields.add(varcharField(SOURCE_TYPE_FIELD, 60));
        fields.add(varcharField(SOURCE_ID_FIELD, 64));
        fields.add(varcharField(CATEGORY_FIELD, 100));
        fields.add(varcharField(SOFTWARE_FIELD, 200));
        fields.add(varcharField(STATUS_FIELD, 40));

        client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withFieldTypes(fields)
                .build());

        log.info("Milvus collection '{}' created", collection);
    }

    private void createIndex() {
        String collection = config.getVectorCollection();
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName(VECTOR_FIELD)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .build());

        client.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        log.info("Milvus collection '{}' indexed and loaded", collection);
    }

    @Override
    public void add(String id, float[] vector, Map<String, String> metadata) {
        String collection = config.getVectorCollection();
        List<InsertParam.Field> fields = scalarInsertSupported
                ? buildScalarInsertFields(id, vector, metadata)
                : buildLegacyInsertFields(id, vector, metadata);

        R<MutationResult> result = insert(collection, fields);
        if (result.getStatus() != R.Status.Success.getCode() && scalarInsertSupported) {
            scalarInsertSupported = false;
            log.warn("Milvus scalar insert failed for id={}, retrying legacy metadata schema: {}", id, result.getMessage());
            result = insert(collection, buildLegacyInsertFields(id, vector, metadata));
        }

        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus insert failed for id={}: {}", id, result.getMessage());
        } else {
            log.debug("Milvus insert success: id={}", id);
        }
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK) {
        return search(queryVector, topK, VectorSearchFilter.none());
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, VectorSearchFilter filter) {
        String expr = scalarSearchSupported ? buildExpr(filter) : null;
        SearchOutcome outcome = searchInternal(queryVector, topK, expr);
        if (expr == null || !outcome.failed() || filter == null || filter.isEmpty()) {
            return outcome.results();
        }
        scalarSearchSupported = false;
        log.warn("Milvus scalar search failed, falling back to metadata filtering");
        return searchInternal(queryVector, topK * 3, null).results().stream()
                .filter(result -> filter.matches(result.getMetadata()))
                .limit(topK)
                .toList();
    }

    private SearchOutcome searchInternal(float[] queryVector, int topK, String expr) {
        String collection = config.getVectorCollection();

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withVectors(Collections.singletonList(toList(queryVector)))
                .withVectorFieldName(VECTOR_FIELD)
                .withTopK(topK)
                .withMetricType(MetricType.COSINE)
                .withParams("{\"nprobe\":16}")
                .withOutFields(Arrays.asList(ID_FIELD, META_FIELD))
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED);
        if (expr != null && !expr.isBlank()) {
            builder.withExpr(expr);
        }
        SearchParam param = builder.build();

        R<SearchResults> result;
        try {
            result = client.search(param);
        } catch (Exception e) {
            reconnectAfterRpcFailure(e);
            return new SearchOutcome(Collections.emptyList(), true);
        }
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus search failed expr={}: {}", expr, result.getMessage());
            return new SearchOutcome(Collections.emptyList(), true);
        }

        List<VectorSearchResult> results = new ArrayList<>();
        SearchResults data = result.getData();
        if (data.getResults() == null || data.getResults().getFieldsDataList().isEmpty()) {
            return new SearchOutcome(results, false);
        }

        int count = data.getResults().getScoresCount();
        for (int i = 0; i < count; i++) {
            float score = data.getResults().getScores(i);
            String id = null;
            String metaJson = null;

            for (io.milvus.grpc.FieldData field : data.getResults().getFieldsDataList()) {
                if (ID_FIELD.equals(field.getFieldName())) {
                    id = field.getScalars().getStringData().getData(i);
                } else if (META_FIELD.equals(field.getFieldName())) {
                    metaJson = field.getScalars().getStringData().getData(i);
                }
            }

            Map<String, String> meta = metaJson != null
                    ? gson.fromJson(metaJson, new TypeToken<Map<String, String>>() {}.getType())
                    : Collections.emptyMap();

            results.add(new VectorSearchResult(id, score, meta));
        }

        return new SearchOutcome(results, false);
    }

    private FieldType varcharField(String name, int maxLength) {
        return FieldType.newBuilder()
                .withName(name)
                .withDataType(DataType.VarChar)
                .withMaxLength(maxLength)
                .build();
    }

    private R<MutationResult> insert(String collection, List<InsertParam.Field> fields) {
        return client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
    }

    private List<InsertParam.Field> buildLegacyInsertFields(String id, float[] vector, Map<String, String> metadata) {
        return Arrays.asList(
                InsertParam.Field.builder()
                        .name(ID_FIELD)
                        .values(Collections.singletonList(id))
                        .build(),
                InsertParam.Field.builder()
                        .name(VECTOR_FIELD)
                        .values(Collections.singletonList(toList(vector)))
                        .build(),
                InsertParam.Field.builder()
                        .name(META_FIELD)
                        .values(Collections.singletonList(gson.toJson(metadata)))
                        .build()
        );
    }

    private List<InsertParam.Field> buildScalarInsertFields(String id, float[] vector, Map<String, String> metadata) {
        List<InsertParam.Field> fields = new ArrayList<>(buildLegacyInsertFields(id, vector, metadata));
        fields.add(stringField(SOURCE_FIELD, metadataValue(metadata, "source")));
        fields.add(stringField(SOURCE_TYPE_FIELD, metadataValue(metadata, "sourceType")));
        fields.add(stringField(SOURCE_ID_FIELD, metadataValue(metadata, "sourceId")));
        fields.add(stringField(CATEGORY_FIELD, metadataValue(metadata, "category")));
        fields.add(stringField(SOFTWARE_FIELD, metadataValue(metadata, "software")));
        fields.add(stringField(STATUS_FIELD, metadataValue(metadata, "status")));
        return fields;
    }

    private InsertParam.Field stringField(String name, String value) {
        return InsertParam.Field.builder()
                .name(name)
                .values(Collections.singletonList(value == null ? "" : value))
                .build();
    }

    private String metadataValue(Map<String, String> metadata, String key) {
        if (metadata == null) {
            return "";
        }
        String value = metadata.get(key);
        return value == null ? "" : value.trim();
    }

    private String buildExpr(VectorSearchFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        List<String> terms = new ArrayList<>();
        addInExpr(terms, SOURCE_FIELD, filter.getSources());
        addInExpr(terms, CATEGORY_FIELD, filter.getCategories());
        addInExpr(terms, SOFTWARE_FIELD, filter.getSoftware());
        addInExpr(terms, SOURCE_TYPE_FIELD, filter.getSourceTypes());
        addInExpr(terms, SOURCE_ID_FIELD, filter.getSourceIds());
        addInExpr(terms, STATUS_FIELD, filter.getStatuses());
        return terms.isEmpty() ? null : String.join(" and ", terms);
    }

    private void addInExpr(List<String> terms, String fieldName, Iterable<String> values) {
        StringJoiner joiner = new StringJoiner(", ", fieldName + " in [", "]");
        int count = 0;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            joiner.add("\"" + escapeExprValue(value) + "\"");
            count++;
        }
        if (count > 0) {
            terms.add(joiner.toString());
        }
    }

    private String escapeExprValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SearchOutcome(List<VectorSearchResult> results, boolean failed) {}

    private synchronized void reconnectAfterRpcFailure(Exception e) {
        log.warn("Milvus search RPC failed, reconnecting client: {}", e.getMessage());
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception closeError) {
            log.debug("Milvus client close failed during reconnect");
        }
        try {
            client = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(config.getVectorHost())
                            .withPort(config.getVectorPort())
                            .build()
            );
            createIndex();
        } catch (Exception reconnectError) {
            log.warn("Milvus reconnect failed: {}", reconnectError.getMessage());
        }
    }

    @Override
    public long count() {
        try {
            io.milvus.param.collection.GetCollectionStatisticsParam param =
                    io.milvus.param.collection.GetCollectionStatisticsParam.newBuilder()
                            .withCollectionName(config.getVectorCollection())
                            .build();
            R<io.milvus.grpc.GetCollectionStatisticsResponse> resp = client.getCollectionStatistics(param);
            if (resp.getStatus() == R.Status.Success.getCode() && resp.getData() != null) {
                String statsJson = resp.getData().getStatsList().stream()
                        .filter(kv -> "row_count".equals(kv.getKey()))
                        .map(io.milvus.grpc.KeyValuePair::getValue)
                        .findFirst().orElse("0");
                return Long.parseLong(statsJson);
            }
        } catch (Exception e) {
            log.warn("[Milvus] Failed to get count: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public void delete(String id) {
        client.delete(DeleteParam.newBuilder()
                .withCollectionName(config.getVectorCollection())
                .withExpr(String.format("%s == \"%s\"", ID_FIELD, escapeExprValue(id)))
                .build());
    }

    private List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }
}
