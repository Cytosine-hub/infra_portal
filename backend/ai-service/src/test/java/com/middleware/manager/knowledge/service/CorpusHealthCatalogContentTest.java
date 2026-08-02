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

class CorpusHealthCatalogContentTest {

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
    @DisplayName("TC-HEALTH-011 页面统计应覆盖全部页面类型而不是只统计经验沉淀")
    void countsAllPageTypes() {
        when(pageMapper.countAll()).thenReturn(9);
        when(pageMapper.countByStatus("ACTIVE")).thenReturn(6);
        when(pageMapper.countByStatus("DRAFT")).thenReturn(3);

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalPages()).isEqualTo(9);
        assertThat(report.getActivePages()).isEqualTo(6);
        assertThat(report.getDraftPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-HEALTH-012 新增来源类型后无需修改健康度代码")
    void futureSourceTypeIsIncludedAutomatically() {
        WikiSource source = source(7L, "外部经验库", "EXTERNAL_KB");
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source));
        when(vectorStore.existsBySource("EXTERNAL_KB", 7L)).thenReturn(true);

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalKnowledgeItems()).isEqualTo(1);
        assertThat(report.getSourceTypeCounts()).containsOnlyKeys("EXTERNAL_KB");
    }

    @Test
    @DisplayName("TC-HEALTH-013 单份正文不应被误报为重复内容")
    void singleContentHashIsNotDuplicate() {
        WikiSource source = source(8L, "唯一内容", "MANUAL");
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(source));
        when(vectorStore.existsBySource(any(), any())).thenReturn(true);

        CorpusHealthReport report = service.report();

        assertThat(report.getDuplicateContentGroups()).isEmpty();
    }

    private WikiSource source(Long id, String title, String sourceType) {
        WikiSource source = new WikiSource();
        source.setId(id);
        source.setTitle(title);
        source.setSourceType(sourceType);
        source.setContent("正文-" + id);
        source.setContentHash("hash-" + id);
        return source;
    }
}
