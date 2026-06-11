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
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "app.vector.type", havingValue = "milvus")
@Slf4j
public class MilvusVectorStore implements VectorStore {
    private static final int VECTOR_DIM = 1024;
    private static final String ID_FIELD = "id";
    private static final String VECTOR_FIELD = "vector";
    private static final String META_FIELD = "metadata";

    private final AiConfig config;
    private final Gson gson = new Gson();
    private MilvusServiceClient client;

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

        List<InsertParam.Field> fields = Arrays.asList(
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

        R<MutationResult> result = client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());

        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus insert failed for id={}: {}", id, result.getMessage());
        } else {
            log.debug("Milvus insert success: id={}", id);
        }
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK) {
        String collection = config.getVectorCollection();

        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withVectors(Collections.singletonList(toList(queryVector)))
                .withVectorFieldName(VECTOR_FIELD)
                .withTopK(topK)
                .withMetricType(MetricType.COSINE)
                .withParams("{\"nprobe\":16}")
                .withOutFields(Arrays.asList(ID_FIELD, META_FIELD))
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build();

        R<SearchResults> result;
        try {
            result = client.search(param);
        } catch (Exception e) {
            reconnectAfterRpcFailure(e);
            return Collections.emptyList();
        }
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus search failed: {}", result.getMessage());
            return Collections.emptyList();
        }

        List<VectorSearchResult> results = new ArrayList<>();
        SearchResults data = result.getData();
        if (data.getResults() == null || data.getResults().getFieldsDataList().isEmpty()) {
            return results;
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

        return results;
    }

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
                .withExpr(String.format("%s == \"%s\"", ID_FIELD, id))
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
