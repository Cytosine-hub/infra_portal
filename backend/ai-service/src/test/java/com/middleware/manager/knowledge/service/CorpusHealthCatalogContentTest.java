package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.service.SoftwareTypeLookup;
import com.middleware.manager.wiki.entity.WikiPage;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class CorpusHealthCatalogContentTest {

    @Mock private ParameterStandardIndexMapper standardMapper;
    @Mock private StandardParameterLookupMapper parameterMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private VectorStore vectorStore;
    @Mock private SoftwareTypeLookup softwareTypeLookup;
    @Mock private WikiPageMapper pageMapper;

    private CorpusHealthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(standardMapper.findPublished()).thenReturn(List.of());
        when(parameterMapper.search(any(), any(), anyInt())).thenReturn(List.of());
        when(sourceMapper.findAll()).thenReturn(List.of());
        when(softwareTypeLookup.findActive()).thenReturn(List.of());
        when(pageMapper.findAllExcludingContent()).thenReturn(List.of());
        when(vectorStore.count()).thenReturn(0L);
        service = new CorpusHealthService(standardMapper, parameterMapper, sourceMapper,
                vectorStore, softwareTypeLookup, pageMapper);
    }

    @Test
    @DisplayName("TC-HEALTH-021 后台启用的软件类型应动态形成标准覆盖率分母")
    void activeAdminSoftwareTypesDriveCoverageDenominator() {
        when(softwareTypeLookup.findActive()).thenReturn(List.of(
                software("数据库", "MySQL"), software("中间件", "Nginx")));

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalCells()).isEqualTo(8);
        assertThat(report.getMissingCells()).hasSize(8);
        assertThat(report.getCatalogSoftwareCount()).isEqualTo(2);
        assertThat(report.isTargetCatalogConfigured()).isTrue();
        assertThat(report.getSoftwareByCategory()).containsEntry("数据库", List.of("MySQL"));
    }

    @Test
    @DisplayName("TC-HEALTH-022 标准与后台软件名称仅大小写不同时应落入同一格")
    void standardMatchesCatalogIgnoringCase() {
        when(softwareTypeLookup.findActive()).thenReturn(
                List.of(software("中间件", "nginx")));
        ParameterStandard standard = new ParameterStandard();
        standard.setCategory("中间件");
        standard.setSoftware("Nginx");
        standard.setTitle("Nginx 参数标准");
        when(standardMapper.findPublished()).thenReturn(List.of(standard));

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalCells()).isEqualTo(4);
        assertThat(report.getCoveredCells()).isEqualTo(1);
        assertThat(report.getMissingCells()).hasSize(3);
    }

    @Test
    @DisplayName("TC-HEALTH-023 后台软件分类变化后下一次健康检查应立即反映")
    void catalogChangesAreReflectedOnEveryReport() {
        when(softwareTypeLookup.findActive())
                .thenReturn(List.of(software("数据库", "MySQL")))
                .thenReturn(List.of(software("数据库", "MySQL"), software("中间件", "Redis")));

        assertThat(service.report().getTotalCells()).isEqualTo(4);
        assertThat(service.report().getTotalCells()).isEqualTo(8);
    }

    @Test
    @DisplayName("TC-HEALTH-024 健康度应分别统计上传文档、标准文档和经验沉淀")
    void countsAllKnowledgeContentTypes() {
        when(sourceMapper.findAll()).thenReturn(List.of(
                source(1L, "UPLOAD"), source(2L, "UPLOAD"),
                source(3L, "STANDARD_DOC"), source(4L, "WEB")));
        when(vectorStore.existsBySource(any(), any())).thenReturn(true);
        when(pageMapper.findAllExcludingContent()).thenReturn(List.of(
                page("经验一", "EXPERIENCE", "ACTIVE"),
                page("经验二", "EXPERIENCE", "ACTIVE"),
                page("经验草稿", "EXPERIENCE", "DRAFT"),
                page("概览", "OVERVIEW", "ACTIVE")));

        CorpusHealthReport report = service.report();

        assertThat(report.getUploadedDocuments()).isEqualTo(2);
        assertThat(report.getStandardDocuments()).isEqualTo(1);
        assertThat(report.getOtherDocuments()).isEqualTo(1);
        assertThat(report.getExperiencePages()).isEqualTo(3);
        assertThat(report.getActiveExperiencePages()).isEqualTo(2);
        assertThat(report.getDraftExperiencePages()).isEqualTo(1);
        assertThat(report.getTotalKnowledgeItems()).isEqualTo(7);
    }

    @Test
    @DisplayName("TC-HEALTH-025 后台软件分类不可用时应降级并明确标记不可信")
    void catalogFailureDegradesWithoutBreakingHealthReport() {
        when(softwareTypeLookup.findActive()).thenThrow(new BusinessException(
                ErrorCode.SOFTWARE_TYPE_LOOKUP_FAILED, ErrorMessages.SOFTWARE_TYPE_LOOKUP_FAILED));

        CorpusHealthReport report = service.report();

        assertThat(report.isCatalogStatusReliable()).isFalse();
        assertThat(report.isTargetCatalogConfigured()).isFalse();
        assertThat(report.getCoverageHint()).contains("查询失败");
    }

    private SoftwareType software(String category, String name) {
        SoftwareType type = new SoftwareType();
        type.setCategory(category);
        type.setName(name);
        type.setActive(true);
        return type;
    }

    private WikiSource source(Long id, String sourceType) {
        WikiSource source = new WikiSource();
        source.setId(id);
        source.setTitle(sourceType + "-" + id);
        source.setSourceType(sourceType);
        return source;
    }

    private WikiPage page(String title, String pageType, String status) {
        WikiPage page = new WikiPage();
        page.setTitle(title);
        page.setPageType(pageType);
        page.setStatus(status);
        return page;
    }
}
