package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.service.SoftwareTypeLookup;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageMapper;
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
    @Mock private SoftwareTypeLookup softwareTypeLookup;
    @Mock private WikiPageMapper pageMapper;

    private String runId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runId = "KBV14R-" + UUID.randomUUID().toString().substring(0, 8);
        when(standardMapper.findPublished()).thenReturn(List.of());
        when(parameterMapper.search(any(), any(), anyInt())).thenReturn(List.of());
        when(sourceMapper.findAll()).thenReturn(List.of());
        when(softwareTypeLookup.findActive()).thenReturn(List.of());
        when(pageMapper.countByPageType(anyString())).thenReturn(0);
        when(pageMapper.countByPageTypeAndStatus(anyString(), anyString())).thenReturn(0);
        when(vectorStore.existsBySource(anyString(), anyLong())).thenReturn(false);
        when(vectorStore.count()).thenReturn(0L);
    }

    private CorpusHealthService service(List<String> catalog) {
        List<SoftwareType> softwareTypes = catalog.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .map(this::softwareType)
                .toList();
        when(softwareTypeLookup.findActive()).thenReturn(softwareTypes);
        return new CorpusHealthService(standardMapper, parameterMapper, sourceMapper,
                vectorStore, softwareTypeLookup, pageMapper);
    }

    private SoftwareType softwareType(String entry) {
        int separator = entry.indexOf(':');
        SoftwareType type = new SoftwareType();
        type.setCategory(separator < 0 ? "未分类" : entry.substring(0, separator).trim());
        type.setName(separator < 0 ? entry.trim() : entry.substring(separator + 1).trim());
        type.setActive(true);
        return type;
    }

    @Test
    @DisplayName("TC-HEALTH-013 后台空白软件类型不得被当成有效清单")
    void blankCatalogEntriesAreNotConfigured() {
        CorpusHealthReport report = service(Arrays.asList("", "   ")).report();

        assertThat(report.isTargetCatalogConfigured()).isFalse();
        assertThat(report.getCoverageHint()).contains("后台管理");
        assertThat(report.getTotalCells()).isZero();
    }

    @Test
    @DisplayName("TC-HEALTH-014 后台软件类型混有空白时应只取有效条目")
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
