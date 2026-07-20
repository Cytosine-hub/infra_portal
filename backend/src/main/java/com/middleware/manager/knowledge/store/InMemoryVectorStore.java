package com.middleware.manager.knowledge.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
