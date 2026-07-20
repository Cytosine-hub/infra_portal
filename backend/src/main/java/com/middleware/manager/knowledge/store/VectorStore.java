package com.middleware.manager.knowledge.store;

import java.util.List;
import java.util.Map;

public interface VectorStore {

    void add(String id, float[] vector, Map<String, String> metadata);

    List<VectorSearchResult> search(float[] queryVector, int topK);

    default List<VectorSearchResult> search(float[] queryVector, int topK, VectorSearchFilter filter) {
        List<VectorSearchResult> results = search(queryVector, topK);
        if (filter == null || filter.isEmpty()) {
            return results;
        }
        return results.stream()
                .filter(result -> filter.matches(result.getMetadata()))
                .limit(topK)
                .toList();
    }

    void delete(String id);

    void createCollection();

    long count();

    class VectorSearchResult {
        private String id;
        private float score;
        private Map<String, String> metadata;

        public VectorSearchResult() {
        }

        public VectorSearchResult(String id, float score, Map<String, String> metadata) {
            this.id = id;
            this.score = score;
            this.metadata = metadata;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
