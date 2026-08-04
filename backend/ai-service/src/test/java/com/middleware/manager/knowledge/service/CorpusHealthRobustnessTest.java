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

class CorpusHealthRobustnessTest {

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
    @DisplayName("TC-HEALTH-008 来源类型统计应忽略大小写并保持动态扩展")
    void sourceTypeCountsAreNormalized() {
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(
                source(1L, "论坛一", "forum_post"),
                source(2L, "论坛二", "FORUM_POST"),
                source(3L, "未来来源", "EXTERNAL_FEED")));
        when(vectorStore.existsBySource(any(), any())).thenReturn(true);

        CorpusHealthReport report = service.report();

        assertThat(report.getSourceTypeCounts())
                .containsEntry("FORUM_POST", 2)
                .containsEntry("EXTERNAL_FEED", 1);
    }

    @Test
    @DisplayName("TC-HEALTH-009 已索引切片数应取向量集合总量")
    void indexedChunksUsesCollectionTotal() {
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source(4L, "大文档", "UPLOAD")));
        when(vectorStore.existsBySource("UPLOAD", 4L)).thenReturn(true);
        when(vectorStore.count()).thenReturn(232L);

        CorpusHealthReport report = service.report();

        assertThat(report.getIndexedChunks()).isEqualTo(232L);
    }

    @Test
    @DisplayName("TC-HEALTH-010 无标题来源应使用可追踪标识而不是空字符串")
    void untitledSourceUsesTraceableLabel() {
        WikiSource source = source(5L, null, "MANUAL");
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source));
        when(vectorStore.existsBySource("MANUAL", 5L)).thenReturn(false);

        CorpusHealthReport report = service.report();

        assertThat(report.getUnindexedSources()).containsExactly("未命名来源 #5");
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
