package com.middleware.manager.knowledge.service;

import com.middleware.manager.knowledge.service.CorpusHealthService.CorpusHealthReport;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.knowledge.store.VectorStore;
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

/**
 * 知识库通用健康度统计，不绑定软件分类或标准文档业务口径。
 */
class CorpusHealthServiceTest {

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
        when(vectorStore.existsBySource(any(), any())).thenReturn(false);
        when(vectorStore.count()).thenReturn(0L);
    }

    @Test
    @DisplayName("TC-HEALTH-001 应动态统计全部来源类型与全部知识页面")
    void countsEverySourceTypeAndPage() {
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(
                source(1L, "上传手册", "UPLOAD", "hash-1", "正文"),
                source(2L, "参数标准", "STANDARD_DOC", "hash-2", "正文"),
                source(3L, "标准文档", "STANDARD_DOCUMENT", "hash-3", "正文"),
                source(4L, "论坛文章", "FORUM_POST", "hash-4", "正文")));
        when(vectorStore.existsBySource(any(), any())).thenReturn(true);
        when(pageMapper.countAll()).thenReturn(6);
        when(pageMapper.countByStatus("ACTIVE")).thenReturn(4);
        when(pageMapper.countByStatus("DRAFT")).thenReturn(2);

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalSources()).isEqualTo(4);
        assertThat(report.getTotalPages()).isEqualTo(6);
        assertThat(report.getTotalKnowledgeItems()).isEqualTo(10);
        assertThat(report.getActivePages()).isEqualTo(4);
        assertThat(report.getDraftPages()).isEqualTo(2);
        assertThat(report.getSourceTypeCounts()).containsEntry("FORUM_POST", 1);
        assertThat(report.getSourceTypeCounts()).containsEntry("STANDARD_DOCUMENT", 1);
    }

    @Test
    @DisplayName("TC-HEALTH-002 应对所有来源检查空内容和重复正文")
    void detectsEmptyAndDuplicateSources() {
        WikiSource empty = source(1L, "空白知识", "MANUAL", null, null);
        WikiSource duplicateA = source(2L, "重复手册 A", "UPLOAD", "same-hash", "相同正文");
        WikiSource duplicateB = source(3L, "重复手册 B", "FORUM_POST", "same-hash", "相同正文");
        WikiSource fileOnly = source(4L, "只有原文件", "UPLOAD", null, null);
        fileOnly.setFilePath("knowledge/manual.pdf");
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(empty, duplicateA, duplicateB, fileOnly));
        when(vectorStore.existsBySource(any(), any())).thenReturn(true);

        CorpusHealthReport report = service.report();

        assertThat(report.getEmptySources()).containsExactly("空白知识");
        assertThat(report.getDuplicateContentGroups()).singleElement()
                .satisfies(group -> assertThat(group).contains("重复手册 A", "重复手册 B"));
    }

    @Test
    @DisplayName("TC-HEALTH-003 未索引判定以向量为准，不看 ingested 字段")
    void reportsUnindexedSources() {
        // KBV-013 修正：原用例按 wiki_sources.ingested 判定，但该字段表示 Wiki 编译
        // 状态，上传类文档恒为 false，会把已可检索的文档误报为未索引。
        WikiSource hasVectors = source(1L, "已向量化.pdf", "UPLOAD", "h1", "正文");
        hasVectors.setIngested(false);
        WikiSource noVectors = source(2L, "无向量.pdf", "UPLOAD", "h2", "正文");
        noVectors.setIngested(true);
        when(sourceMapper.findAllForHealth()).thenReturn(List.of(hasVectors, noVectors));
        when(vectorStore.existsBySource("UPLOAD", 1L)).thenReturn(true);
        when(vectorStore.existsBySource("UPLOAD", 2L)).thenReturn(false);

        CorpusHealthReport report = service.report();

        assertThat(report.getTotalSources()).isEqualTo(2);
        assertThat(report.getUnindexedSources()).containsExactly("无向量.pdf");
    }

    @Test
    @DisplayName("TC-HEALTH-004 空语料不应抛异常，各项应给出零值而非崩溃")
    void emptyCorpusIsSafe() {
        CorpusHealthReport report = service.report();

        assertThat(report.getTotalSources()).isZero();
        assertThat(report.getTotalPages()).isZero();
        assertThat(report.getSourceTypeCounts()).isEmpty();
        assertThat(report.getEmptySources()).isEmpty();
    }

    private WikiSource source(Long id, String title, String sourceType, String hash, String content) {
        WikiSource s = new WikiSource();
        s.setId(id);
        s.setTitle(title);
        s.setSourceType(sourceType);
        s.setContentHash(hash);
        s.setContent(content);
        return s;
    }
}
