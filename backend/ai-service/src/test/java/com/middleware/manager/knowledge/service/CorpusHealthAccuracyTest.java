package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.service.SoftwareTypeLookup;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * KBV-013 / KBV-014：语料健康度的判定口径修正。
 * <p>数据隔离：每轮用唯一 runId 构造夹具，不复用既有的 6 份问题语料；
 * tearDown 清空本轮夹具并校验无残留。
 */
class CorpusHealthAccuracyTest {

    @Mock private ParameterStandardIndexMapper standardMapper;
    @Mock private StandardParameterLookupMapper parameterMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private VectorStore vectorStore;
    @Mock private SoftwareTypeLookup softwareTypeLookup;
    @Mock private WikiPageMapper pageMapper;

    private String runId;
    private final List<Object> fixtures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runId = "KBV13-" + UUID.randomUUID().toString().substring(0, 8);
        fixtures.clear();
        when(standardMapper.findPublished()).thenReturn(List.of());
        when(parameterMapper.search(any(), any(), anyInt())).thenReturn(List.of());
        when(sourceMapper.findAll()).thenReturn(List.of());
        when(softwareTypeLookup.findActive()).thenReturn(List.of());
        when(pageMapper.findAllExcludingContent()).thenReturn(List.of());
        when(vectorStore.existsBySource(anyString(), anyLong())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        fixtures.clear();
        assertThat(fixtures).as("本轮夹具应已清理").isEmpty();
    }

    private CorpusHealthService service(List<String> targetCatalog) {
        List<SoftwareType> softwareTypes = targetCatalog.stream()
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

    private WikiSource source(Long id, String suffix, boolean ingestedFlag) {
        WikiSource s = new WikiSource();
        s.setId(id);
        s.setTitle(runId + "-" + suffix);
        s.setSourceType("UPLOAD");
        s.setIngested(ingestedFlag);
        fixtures.add(s);
        return s;
    }

    @Nested
    @DisplayName("KBV-013 未索引判定应以向量为准")
    class UnindexedDetection {

        @Test
        @DisplayName("TC-HEALTH-006 有向量切片的文档不得因 ingested=false 被误报为未索引")
        void documentWithVectorsIsNotReportedUnindexed() {
            // ingested 表示 Wiki 编译状态，不代表向量是否存在。
            // 上传类文档从不参与 Wiki 编译，该字段恒为 false，但切片确实可检索。
            WikiSource doc = source(1L, "已向量化.pdf", false);
            when(sourceMapper.findAll()).thenReturn(List.of(doc));
            when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);
            when(vectorStore.count()).thenReturn(34L);

            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.getUnindexedSources()).isEmpty();
            assertThat(report.getIndexedChunks()).isEqualTo(34L);
        }

        @Test
        @DisplayName("TC-HEALTH-007 确实没有向量的文档应被列为未索引")
        void documentWithoutVectorsIsReported() {
            WikiSource doc = source(2L, "未向量化.pdf", true);
            when(sourceMapper.findAll()).thenReturn(List.of(doc));
            when(vectorStore.existsBySource("UPLOAD", 2L)).thenReturn(false);

            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.getUnindexedSources()).containsExactly(doc.getTitle());
        }

        @Test
        @DisplayName("TC-HEALTH-008 向量库不可用时应降级为不下结论，而非把全部文档误报为未索引")
        void vectorStoreFailureDoesNotFalselyReportAll() {
            WikiSource doc = source(3L, "查询失败.pdf", false);
            when(sourceMapper.findAll()).thenReturn(List.of(doc));
            when(vectorStore.existsBySource("UPLOAD", 3L))
                    .thenThrow(new RuntimeException("Milvus 不可达"));

            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.getUnindexedSources()).isEmpty();
            assertThat(report.isIndexStatusReliable()).isFalse();
        }
    }

    @Nested
    @DisplayName("审查补充：多来源与边界")
    class ReviewGaps {

        @Test
        @DisplayName("TC-HEALTH-017 部分来源查询失败时整体判定不可信，且不输出局部数字")
        void partialFailureDoesNotLeakPartialNumbers() {
            WikiSource ok = source(1L, "正常.pdf", false);
            WikiSource bad = source(2L, "查询失败.pdf", false);
            when(sourceMapper.findAll()).thenReturn(List.of(ok, bad));
            when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);
            when(vectorStore.existsBySource("UPLOAD", 2L))
                    .thenThrow(new RuntimeException("Milvus 不可达"));
            when(vectorStore.count()).thenReturn(100L);

            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.isIndexStatusReliable()).isFalse();
            assertThat(report.getUnindexedSources()).isEmpty();
            assertThat(report.getIndexedChunks()).isZero();
        }

        @Test
        @DisplayName("TC-HEALTH-018 来源缺少 sourceType 时不应崩溃")
        void nullSourceTypeIsSafe() {
            WikiSource broken = new WikiSource();
            broken.setId(9L);
            broken.setTitle(runId + "-缺类型");
            broken.setSourceType(null);
            when(sourceMapper.findAll()).thenReturn(List.of(broken));
            when(vectorStore.existsBySource(null, 9L)).thenReturn(false);

            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.getUnindexedSources()).containsExactly(broken.getTitle());
        }

        @Test
        @DisplayName("TC-HEALTH-019 同名软件分属不同分类时应各占独立格子")
        void sameSoftwareInDifferentCategories() {
            CorpusHealthReport report =
                    service(List.of("中间件:Nginx", "应用:Nginx")).report();

            // 两个分类各 4 格，不得塌缩成 4
            assertThat(report.getTotalCells()).isEqualTo(8);
        }

        @Test
        @DisplayName("TC-HEALTH-020 标题无法归类的标准应单列，避免被当成未写")
        void unclassifiedStandardIsListed() {
            ParameterStandard s = new ParameterStandard();
            s.setCategory("数据库");
            s.setSoftware("MySQL");
            s.setTitle(runId + " 一份没有类型关键词的文档");
            s.setContent("正文");
            when(standardMapper.findPublished()).thenReturn(List.of(s));

            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.getUnclassifiedStandards()).hasSize(1);
            assertThat(report.getUnclassifiedStandards().get(0)).contains("MySQL");
        }
    }

    @Nested
    @DisplayName("KBV-014 覆盖率分母应独立于现有标准")
    class CoverageDenominator {

        @Test
        @DisplayName("TC-HEALTH-009 后台启用软件后，未录入任何标准的软件也应计入分母并列为空缺")
        void targetCatalogDrivesDenominator() {
            // 分母来自后台启用的软件类型，不从现有标准反推——否则整套语料空白时
            // 分母也是 0，覆盖率显示 0/0，反而看不出问题
            CorpusHealthReport report =
                    service(List.of("数据库:MySQL", "中间件:Nginx")).report();

            assertThat(report.getTotalCells()).isEqualTo(8);
            assertThat(report.getCoveredCells()).isZero();
            assertThat(report.getMissingCells()).hasSize(8);
            assertThat(report.getMissingCells()).anySatisfy(c -> assertThat(c).contains("MySQL"));
            assertThat(report.getMissingCells()).anySatisfy(c -> assertThat(c).contains("Nginx"));
        }

        @Test
        @DisplayName("TC-HEALTH-010 已发布标准应正确落格，其余格子仍列为空缺")
        void publishedStandardsFillCells() {
            ParameterStandard s = new ParameterStandard();
            s.setCategory("数据库");
            s.setSoftware("MySQL");
            s.setTitle(runId + " MySQL 参数标准");
            s.setContent("正文");
            when(standardMapper.findPublished()).thenReturn(List.of(s));

            CorpusHealthReport report = service(List.of("数据库:MySQL")).report();

            assertThat(report.getTotalCells()).isEqualTo(4);
            assertThat(report.getCoveredCells()).isEqualTo(1);
            assertThat(report.getMissingCells()).hasSize(3);
            assertThat(report.getMissingCells()).noneSatisfy(c -> assertThat(c).contains("参数"));
        }

        @Test
        @DisplayName("TC-HEALTH-011 后台未配置启用软件时应显式标注，不得用 0/0 掩盖")
        void unconfiguredCatalogIsExplicit() {
            CorpusHealthReport report = service(List.of()).report();

            assertThat(report.isTargetCatalogConfigured()).isFalse();
            assertThat(report.getCoverageHint()).contains("后台管理");
        }

        @Test
        @DisplayName("TC-HEALTH-012 后台清单外但已录入标准的软件也应计入，避免遗漏真实语料")
        void standardsOutsideCatalogStillCounted() {
            ParameterStandard s = new ParameterStandard();
            s.setCategory("主机");
            s.setSoftware("Linux");
            s.setTitle(runId + " Linux 部署标准");
            s.setContent("正文");
            when(standardMapper.findPublished()).thenReturn(List.of(s));

            CorpusHealthReport report = service(List.of("数据库:MySQL")).report();

            // MySQL 4 格 + Linux 4 格
            assertThat(report.getTotalCells()).isEqualTo(8);
            assertThat(report.getCoveredCells()).isEqualTo(1);
        }
    }
}
