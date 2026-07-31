package com.middleware.manager.knowledge.service;

import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 语料健康度的配置与计数健壮性。
 * <p>两处隐患：Spring 把空配置转成 List 时可能得到 {@code [""]} 而非空 List，
 * 用 isEmpty() 判定「是否已配置」会误判；以及索引状态只需存在性判断，
 * 不该按切片全量计数（Milvus query 有默认条数上限，会截断）。
 */
class CorpusHealthRobustnessTest {

    @Mock private ParameterStandardIndexMapper standardMapper;
    @Mock private StandardParameterLookupMapper parameterMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private VectorStore vectorStore;

    private String runId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runId = "KBV14R-" + UUID.randomUUID().toString().substring(0, 8);
        when(standardMapper.findPublished()).thenReturn(List.of());
        when(parameterMapper.search(any(), any(), anyInt())).thenReturn(List.of());
        when(sourceMapper.findAll()).thenReturn(List.of());
        when(vectorStore.existsBySource(anyString(), anyLong())).thenReturn(false);
        when(vectorStore.count()).thenReturn(0L);
    }

    private CorpusHealthService service(List<String> catalog) {
        return new CorpusHealthService(standardMapper, parameterMapper, sourceMapper,
                vectorStore, catalog);
    }

    @Test
    @DisplayName("TC-HEALTH-013 空白配置项不得被当成已配置目标清单")
    void blankCatalogEntriesAreNotConfigured() {
        // Spring 将 ${app.corpus.target-catalog:} 转 List 时可能给出 [""]，
        // 直接用 isEmpty() 判定会把「没配」误判成「已配」
        CorpusHealthReport report = service(Arrays.asList("", "   ")).report();

        assertThat(report.isTargetCatalogConfigured()).isFalse();
        assertThat(report.getCoverageHint()).contains("目标清单");
        assertThat(report.getTotalCells()).isZero();
    }

    @Test
    @DisplayName("TC-HEALTH-014 配置项混有空白时应只取有效条目")
    void ignoresBlankEntriesAmongValidOnes() {
        CorpusHealthReport report = service(Arrays.asList("数据库:MySQL", "", "  ")).report();

        assertThat(report.isTargetCatalogConfigured()).isTrue();
        assertThat(report.getTotalCells()).isEqualTo(4);
    }

    @Test
    @DisplayName("TC-HEALTH-015 索引状态判定只需存在性，不得依赖切片全量计数")
    void indexJudgementUsesExistenceNotFullCount() {
        WikiSource doc = source(1L, "大文档.pdf");
        when(sourceMapper.findAll()).thenReturn(List.of(doc));
        // 切片数远超 Milvus query 默认上限时，全量计数会被截断；存在性判断不受影响
        when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);

        CorpusHealthReport report = service(List.of()).report();

        assertThat(report.getUnindexedSources()).isEmpty();
        assertThat(report.isIndexStatusReliable()).isTrue();
    }

    @Test
    @DisplayName("TC-HEALTH-016 已索引切片总数应取集合总量，避免逐源累加被截断")
    void indexedChunksComesFromCollectionTotal() {
        when(sourceMapper.findAll()).thenReturn(List.of(source(1L, "文档.pdf")));
        when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);
        when(vectorStore.count()).thenReturn(232L);

        CorpusHealthReport report = service(List.of()).report();

        assertThat(report.getIndexedChunks()).isEqualTo(232L);
    }

    private WikiSource source(Long id, String suffix) {
        WikiSource s = new WikiSource();
        s.setId(id);
        s.setTitle(runId + "-" + suffix);
        s.setSourceType("UPLOAD");
        return s;
    }
}
