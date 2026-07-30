package com.middleware.manager.knowledge.store;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VectorStore {

    void add(String id, float[] vector, Map<String, String> metadata);

    default void addAll(List<VectorRecord> records) {
        for (VectorRecord record : records) {
            add(record.id(), record.vector(), record.metadata());
        }
    }

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

    /**
     * 按来源批量删除该源的全部切片。
     * <p>此前的做法是「把旧内容重新切一遍数出片数」再逐个猜 ID 删除，切片逻辑一改
     * 或内容一变就会留下孤儿向量。改由存储层按条件删除，不依赖对片数的猜测。
     * <p>默认实现为空操作，供不支持条件删除的存储（如内存实现）使用。
     */
    default void deleteBySource(String sourceType, Long sourceId) {
    }

    /**
     * 删除某来源中已不属于当前版本的切片。调用方应先完整写入当前版本，避免更新失败时
     * 提前破坏旧索引。默认实现为空操作，不支持条件删除的实现可保留少量旧切片。
     */
    default void deleteBySourceExcept(String sourceType, Long sourceId, Set<String> retainedIds) {
    }

    void createCollection();

    long count();

    record VectorRecord(String id, float[] vector, Map<String, String> metadata) {
    }

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
