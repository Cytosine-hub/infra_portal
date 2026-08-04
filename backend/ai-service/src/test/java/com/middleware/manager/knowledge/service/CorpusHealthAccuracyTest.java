package com.middleware.manager.knowledge.service;

import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CorpusHealthAccuracyTest {

    @Mock private WikiSourceMapper sourceMapper;
    @Mock private VectorStore vectorStore;
    @Mock private WikiPageMapper pageMapper;

    private CorpusHealthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CorpusHealthService(sourceMapper, vectorStore, pageMapper);
        when(sourceMapper.findAllForHealth()).thenReturn(List.of());
        when(pageMapper.countAll()).thenReturn(0);
        when(pageMapper.countByStatus(any())).thenReturn(0);
        when(vectorStore.count()).thenReturn(0L);
    }

    @Test
    @DisplayName("TC-HEALTH-005 有向量的来源不得因 ingested=false 被误报")
    void indexedSourceIsNotReported() {
        WikiSource source = source(1L, "已向量化", "UPLOAD");
        source.setIngested(false);
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source));
        when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);

        CorpusHealthReport report = service.report();

        assertThat(report.getUnindexedSources()).isEmpty();
        assertThat(report.isIndexStatusReliable()).isTrue();
    }

    @Test
    @DisplayName("TC-HEALTH-006 向量库不可用时不应输出误导性的局部结论")
    void vectorFailureMakesIndexStatusUnreliable() {
        WikiSource source = source(2L, "索引状态未知", "FORUM_POST");
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source));
        when(vectorStore.existsBySource("FORUM_POST", 2L))
                .thenThrow(new IllegalStateException("Milvus unavailable"));

        CorpusHealthReport report = service.report();

        assertThat(report.isIndexStatusReliable()).isFalse();
        assertThat(report.getUnindexedSources()).isEmpty();
        assertThat(report.getIndexedChunks()).isZero();
    }

    @Test
    @DisplayName("TC-HEALTH-007 来源类型缺失时仍应纳入健康检查")
    void missingSourceTypeIsCounted() {
        WikiSource source = source(3L, "缺少来源类型", null);
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source));
        when(vectorStore.existsBySource(null, 3L)).thenReturn(false);

        CorpusHealthReport report = service.report();

        assertThat(report.getSourceTypeCounts()).containsEntry("UNKNOWN", 1);
        assertThat(report.getUnindexedSources()).containsExactly("缺少来源类型");
    }

    private WikiSource source(Long id, String title, String sourceType) {
        WikiSource source = new WikiSource();
        source.setId(id);
        source.setTitle(title);
        source.setSourceType(sourceType);
        source.setContent("正文");
        source.setContentHash("hash-" + id);
        return source;
    }
}
