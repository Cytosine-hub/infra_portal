package com.middleware.manager.knowledge.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryVectorStore 是 {@code matchIfMissing = true} 的默认实现（本地无 Milvus 时生效）。
 * <p>若它不覆写 existsBySource 而沿用接口默认的 false，语料健康度会把全部文档判为
 * 「未索引」，且因为不抛异常，indexStatusReliable 仍是 true——这正是 KBV-013 要修的
 * 那类误报，只是换了个触发场景。
 */
class InMemoryVectorStoreSourceTest {

    private Map<String, String> meta(String sourceType, String sourceId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sourceType", sourceType);
        m.put("sourceId", sourceId);
        m.put("content", "正文");
        return m;
    }

    @Test
    @DisplayName("TC-VECTOR-003 内存实现应按来源真实判断切片是否存在")
    void reportsExistingSource() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add("knowledge_source_7_0", new float[]{0.1f, 0.2f}, meta("UPLOAD", "7"));

        assertThat(store.existsBySource("UPLOAD", 7L)).isTrue();
    }

    @Test
    @DisplayName("TC-VECTOR-004 没有对应来源切片时应返回 false")
    void reportsMissingSource() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add("knowledge_source_7_0", new float[]{0.1f, 0.2f}, meta("UPLOAD", "7"));

        assertThat(store.existsBySource("UPLOAD", 99L)).isFalse();
        assertThat(store.existsBySource("STANDARD_DOC", 7L)).isFalse();
    }

    @Test
    @DisplayName("TC-VECTOR-005 删除来源后存在性判断应同步变为 false")
    void reflectsDeletion() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add("knowledge_source_7_0", new float[]{0.1f, 0.2f}, meta("UPLOAD", "7"));
        store.deleteBySource("UPLOAD", 7L);

        assertThat(store.existsBySource("UPLOAD", 7L)).isFalse();
    }

    @Test
    @DisplayName("TC-VECTOR-006 参数为空时不得误判为存在")
    void nullArgumentsAreSafe() {
        InMemoryVectorStore store = new InMemoryVectorStore();
        store.add("knowledge_source_7_0", new float[]{0.1f, 0.2f}, meta("UPLOAD", "7"));

        assertThat(store.existsBySource(null, 7L)).isFalse();
        assertThat(store.existsBySource("UPLOAD", null)).isFalse();
    }
}
