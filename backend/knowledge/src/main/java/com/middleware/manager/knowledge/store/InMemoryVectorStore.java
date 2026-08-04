package com.middleware.manager.knowledge.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.vector.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private final ConcurrentHashMap<String, float[]> vectors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> metadataStore = new ConcurrentHashMap<>();

    @Override
    public void add(String id, float[] vector, Map<String, String> metadata) {
        vectors.put(id, vector.clone());
        metadataStore.put(id, metadata);
    }

    @Override
    public synchronized void addAll(List<VectorRecord> records) {
        Map<String, float[]> stagedVectors = new LinkedHashMap<>();
        Map<String, Map<String, String>> stagedMetadata = new LinkedHashMap<>();
        for (VectorRecord record : records) {
            stagedVectors.put(record.id(), record.vector().clone());
            stagedMetadata.put(record.id(), record.metadata());
        }
        vectors.putAll(stagedVectors);
        metadataStore.putAll(stagedMetadata);
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK) {
        return search(queryVector, topK, VectorSearchFilter.none());
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK, VectorSearchFilter filter) {
        List<VectorSearchResult> results = new ArrayList<>();

        for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
            float score = cosineSimilarity(queryVector, entry.getValue());
            Map<String, String> meta = metadataStore.get(entry.getKey());
            if (filter != null && !filter.matches(meta)) {
                continue;
            }
            results.add(new VectorSearchResult(entry.getKey(), score, meta));
        }

        results.sort(new Comparator<VectorSearchResult>() {
            @Override
            public int compare(VectorSearchResult a, VectorSearchResult b) {
                return Float.compare(b.getScore(), a.getScore());
            }
        });

        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        return results;
    }

    @Override
    public void delete(String id) {
        vectors.remove(id);
        metadataStore.remove(id);
    }

    @Override
    public synchronized void deleteBySourceExcept(String sourceType, Long sourceId, Set<String> retainedIds) {
        if (sourceType == null || sourceId == null || retainedIds == null) {
            return;
        }
        String sourceIdValue = String.valueOf(sourceId);
        List<String> staleIds = metadataStore.entrySet().stream()
                .filter(entry -> sourceType.equals(entry.getValue().get("sourceType")))
                .filter(entry -> sourceIdValue.equals(entry.getValue().get("sourceId")))
                .map(Map.Entry::getKey)
                .filter(id -> !retainedIds.contains(id))
                .toList();
        staleIds.forEach(this::delete);
    }

    @Override
    public synchronized boolean existsBySource(String sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) {
            return false;
        }
        // 必须真实判断：本类是 matchIfMissing=true 的默认实现，沿用接口默认的 false
        // 会让语料健康度把全部文档误判为未索引，且因不抛异常而显示为「结论可信」
        String sourceIdValue = String.valueOf(sourceId);
        return metadataStore.values().stream()
                .anyMatch(meta -> sourceType.equals(meta.get("sourceType"))
                        && sourceIdValue.equals(meta.get("sourceId")));
    }

    @Override
    public void deleteBySource(String sourceType, Long sourceId) {
        deleteBySourceExcept(sourceType, sourceId, Set.of());
    }

    @Override
    public void createCollection() {
        // No-op for in-memory store
    }

    @Override
    public long count() {
        return vectors.size();
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0f;
        }
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denominator = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        if (denominator == 0.0f) {
            return 0.0f;
        }
        return dotProduct / denominator;
    }
}
