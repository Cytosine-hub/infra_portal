package com.middleware.manager.knowledge.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

    @Test
    @DisplayName("向量检索支持按分类、软件、来源类型和来源ID过滤")
    void searchFiltersByMetadata() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add("middleware-bes", new float[]{1.0f, 0.0f}, Map.of(
                "source", "knowledge",
                "category", "中间件",
                "software", "BES",
                "sourceType", "UPLOAD",
                "sourceId", "11"));
        store.add("database-mysql", new float[]{0.9f, 0.1f}, Map.of(
                "source", "knowledge",
                "category", "数据库",
                "software", "MySQL",
                "sourceType", "STANDARD_DOC",
                "sourceId", "22"));

        VectorSearchFilter filter = VectorSearchFilter.none()
                .addCategory("中间件")
                .addSoftware("BES")
                .addSourceType("UPLOAD")
                .addSourceId(11L);

        List<VectorStore.VectorSearchResult> results = store.search(new float[]{1.0f, 0.0f}, 5, filter);

        assertEquals(1, results.size());
        assertEquals("middleware-bes", results.get(0).getId());
    }

    @Test
    @DisplayName("空过滤条件保持原检索行为")
    void emptyFilterKeepsOriginalSearch() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add("a", new float[]{1.0f, 0.0f}, Map.of("category", "中间件"));
        store.add("b", new float[]{0.0f, 1.0f}, Map.of("category", "数据库"));

        List<VectorStore.VectorSearchResult> results = store.search(new float[]{1.0f, 0.0f}, 2, VectorSearchFilter.none());

        assertEquals(2, results.size());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }
}
