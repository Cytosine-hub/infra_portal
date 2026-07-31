package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.repository.StandardParameterLookupMapper;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.knowledge.store.VectorStore;
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

/**
 * 语料健康度统计。
 * <p>手册第九节把语料健康层称为「真正的瓶颈所在」，但首次实测时 7 项指标有 5 项
 * 标注为「无法评分」——不是数据不好，是根本没有工具去算。本服务补上可确定性计算
 * 的那几项，其中覆盖率矩阵本身就是团队的内容路线图：哪个格子空着就该先写哪个。
 */
class CorpusHealthServiceTest {

    @Mock private ParameterStandardIndexMapper standardMapper;
    @Mock private StandardParameterLookupMapper parameterMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private VectorStore vectorStore;

    private CorpusHealthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CorpusHealthService(standardMapper, parameterMapper, sourceMapper,
                vectorStore, List.of());
        when(standardMapper.findPublished()).thenReturn(List.of());
        when(parameterMapper.search(any(), any(), anyInt())).thenReturn(List.of());
        when(sourceMapper.findAll()).thenReturn(List.of());
        when(vectorStore.existsBySource(any(), any())).thenReturn(false);
    }

    private ParameterStandard standard(String category, String software, String title) {
        ParameterStandard s = new ParameterStandard();
        s.setCategory(category);
        s.setSoftware(software);
        s.setTitle(title);
        s.setContent("正文");
        return s;
    }

    @Test
    @DisplayName("TC-HEALTH-001 覆盖率矩阵应按软件 × 标准类型统计已填与空缺格子")
    void buildsCoverageMatrix() {
        when(standardMapper.findPublished()).thenReturn(List.of(
                standard("数据库", "MySQL", "MySQL 参数标准"),
                standard("数据库", "MySQL", "MySQL 监控标准"),
                standard("中间件", "Nginx", "Nginx 部署标准")));

        CorpusHealthReport report = service.report();

        assertThat(report.getCoveredCells()).isEqualTo(3);
        assertThat(report.getCoverage()).isGreaterThan(0.0);
        // 空缺应能被列出来，直接当作内容待办
        assertThat(report.getMissingCells()).anySatisfy(cell -> {
            assertThat(cell).contains("MySQL");
            assertThat(cell).contains("应急");
        });
    }

    @Test
    @DisplayName("TC-HEALTH-002 同一参数在不同已发布标准取值不同应被识别为矛盾")
    void detectsConflictingParameters() {
        when(parameterMapper.search(null, null, Integer.MAX_VALUE)).thenReturn(List.of(
                row("MySQL", "innodb_buffer_pool_size", "物理内存70%", "MySQL 参数标准"),
                row("MySQL", "innodb_buffer_pool_size", "8G", "MySQL 部署标准")));

        CorpusHealthReport report = service.report();

        assertThat(report.getParameterConflicts()).hasSize(1);
        assertThat(report.getParameterConflicts().get(0)).contains("innodb_buffer_pool_size");
    }

    @Test
    @DisplayName("TC-HEALTH-003 同一参数在不同标准取值一致不算矛盾")
    void identicalValueIsNotConflict() {
        when(parameterMapper.search(null, null, Integer.MAX_VALUE)).thenReturn(List.of(
                row("MySQL", "max_connections", "1000", "MySQL 参数标准"),
                row("MySQL", "max_connections", "1000", "MySQL 部署标准")));

        CorpusHealthReport report = service.report();

        assertThat(report.getParameterConflicts()).isEmpty();
    }

    @Test
    @DisplayName("TC-HEALTH-004 未索引判定以向量为准，不看 ingested 字段")
    void reportsUnindexedSources() {
        // KBV-013 修正：原用例按 wiki_sources.ingested 判定，但该字段表示 Wiki 编译
        // 状态，上传类文档恒为 false，会把已可检索的文档误报为未索引。
        WikiSource hasVectors = source("已向量化.pdf", false);
        hasVectors.setId(1L);
        WikiSource noVectors = source("无向量.pdf", true);
        noVectors.setId(2L);
        when(sourceMapper.findAll()).thenReturn(List.of(hasVectors, noVectors));
        when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);
        when(vectorStore.existsBySource("UPLOAD", 2L)).thenReturn(false);

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalSources()).isEqualTo(2);
        assertThat(report.getUnindexedSources()).containsExactly("无向量.pdf");
    }

    @Test
    @DisplayName("TC-HEALTH-005 空语料不应抛异常，各项应给出零值而非崩溃")
    void emptyCorpusIsSafe() {
        CorpusHealthReport report = service.report();

        assertThat(report.getCoveredCells()).isZero();
        assertThat(report.getParameterConflicts()).isEmpty();
        assertThat(report.getTotalSources()).isZero();
    }

    private com.middleware.manager.knowledge.service.ParameterAnswerRow row(
            String software, String code, String value, String standardTitle) {
        var r = new com.middleware.manager.knowledge.service.ParameterAnswerRow();
        r.setSoftware(software);
        r.setCode(code);
        r.setValue(value);
        r.setStandardTitle(standardTitle);
        return r;
    }

    private WikiSource source(String title, boolean ingested) {
        WikiSource s = new WikiSource();
        s.setTitle(title);
        s.setSourceType("UPLOAD");
        s.setIngested(ingested);
        return s;
    }
}
