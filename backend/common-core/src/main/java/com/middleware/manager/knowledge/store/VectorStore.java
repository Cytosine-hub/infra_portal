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

    /**
     * 混合检索：稠密向量（语义）+ BM25 稀疏向量（精确 token），由存储层做 RRF 融合。
     * <p>运维查询里充斥参数名、错误码、命令这类稀有精确 token，稠密向量会把它们抹平；
     * 而「主从延迟怎么处理」这类语义查询 BM25 又无能为力，两者必须互补。
     * <p>默认实现退化为纯向量检索，供不支持稀疏向量的存储（如内存实现）使用。
     */
    default List<VectorSearchResult> hybridSearch(String queryText, float[] queryVector,
                                                  int topK, VectorSearchFilter filter) {
        return search(queryVector, topK, filter);
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
