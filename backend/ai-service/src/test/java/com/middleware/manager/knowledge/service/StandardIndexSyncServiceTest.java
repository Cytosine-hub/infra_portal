package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.repository.ParameterStandardIndexMapper;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 参数标准 → 知识库索引的自动同步。
 * <p>standards 在 core-service、知识库在 ai-service，是两个进程，但共用同一个库。
 * 采用拉取式对账而非发布时推送：推送失败会让索引静默陈旧且无人发现，
 * 而对账每次都比对状态，本身是自愈的。
 */
class StandardIndexSyncServiceTest {

    @Mock private ParameterStandardIndexMapper standardMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private KnowledgeService knowledgeService;

    private StandardIndexSyncService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StandardIndexSyncService(standardMapper, sourceMapper, knowledgeService);
        when(knowledgeService.removeStandardIfUnindexable(any(ParameterStandard.class))).thenReturn(false);
        when(knowledgeService.indexStandard(any(ParameterStandard.class))).thenReturn(importResult(1));
    }

    private ParameterStandard standard(Long id, String title, String content) {
        ParameterStandard s = new ParameterStandard();
        s.setId(id);
        s.setTitle(title);
        s.setCategory("数据库");
        s.setSoftware("MySQL");
        s.setContent(content);
        s.setStatus("PUBLISHED");
        return s;
    }

    private WikiSource indexed(Long standardId, String title, String hash) {
        WikiSource s = new WikiSource();
        s.setId(100L + standardId);
        s.setTitle(title);
        s.setSourceType("STANDARD_DOC");
        s.setContentHash(hash);
        return s;
    }

    @Test
    @DisplayName("TC-SYNC-001 新发布的标准应被索引")
    void indexesNewlyPublishedStandard() {
        when(standardMapper.findPublished())
                .thenReturn(List.of(standard(1L, "MySQL 参数标准", "innodb_buffer_pool_size 建议物理内存 70%")));
        when(sourceMapper.findAllByType("STANDARD_DOC")).thenReturn(List.of());

        StandardIndexSyncService.SyncReport report = service.sync();

        verify(knowledgeService).indexStandard(any(ParameterStandard.class));
        assertThat(report.getIndexed()).isEqualTo(1);
        assertThat(report.getSkipped()).isZero();
    }

    @Test
    @DisplayName("TC-SYNC-002 内容未变的标准应跳过，不重复消耗 embedding")
    void skipsUnchangedStandard() {
        ParameterStandard std = standard(1L, "MySQL 参数标准", "内容没变");
        when(standardMapper.findPublished()).thenReturn(List.of(std));
        when(sourceMapper.findAllByType("STANDARD_DOC"))
                .thenReturn(List.of(indexed(1L, "MySQL 参数标准", service.hashOf(std))));

        StandardIndexSyncService.SyncReport report = service.sync();

        verify(knowledgeService, never()).indexStandard(any());
        assertThat(report.getSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SYNC-003 内容变更的标准应重新索引")
    void reindexesChangedStandard() {
        when(standardMapper.findPublished())
                .thenReturn(List.of(standard(1L, "MySQL 参数标准", "改过的新内容")));
        when(sourceMapper.findAllByType("STANDARD_DOC"))
                .thenReturn(List.of(indexed(1L, "MySQL 参数标准", "旧内容的哈希")));

        StandardIndexSyncService.SyncReport report = service.sync();

        verify(knowledgeService).indexStandard(any(ParameterStandard.class));
        assertThat(report.getIndexed()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SYNC-004 已取消发布的标准应从索引移除，避免检索到已撤下的内容")
    void removesUnpublishedStandard() {
        when(standardMapper.findPublished()).thenReturn(List.of());
        when(sourceMapper.findAllByType("STANDARD_DOC"))
                .thenReturn(List.of(indexed(9L, "已撤下的标准", "hash")));

        StandardIndexSyncService.SyncReport report = service.sync();

        verify(knowledgeService).deleteDocument("已撤下的标准", "STANDARD_DOC");
        assertThat(report.getRemoved()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SYNC-005 单篇索引失败不应中断整批对账")
    void singleFailureDoesNotAbortBatch() {
        when(standardMapper.findPublished()).thenReturn(List.of(
                standard(1L, "会失败的标准", "内容一"),
                standard(2L, "正常的标准", "内容二")));
        when(sourceMapper.findAllByType("STANDARD_DOC")).thenReturn(List.of());
        doThrow(new RuntimeException("embedding 服务不可达"))
                .when(knowledgeService).indexStandard(argThatTitle("会失败的标准"));

        StandardIndexSyncService.SyncReport report = service.sync();

        assertThat(report.getIndexed()).isEqualTo(1);
        assertThat(report.getFailed()).isEqualTo(1);
    }

    private ParameterStandard argThatTitle(String title) {
        return org.mockito.ArgumentMatchers.argThat(s -> s != null && title.equals(s.getTitle()));
    }

    @Test
    @DisplayName("TC-SYNC-006 正文为空的标准应跳过，不产生空切片")
    void skipsBlankContent() {
        when(standardMapper.findPublished()).thenReturn(List.of(standard(1L, "空标准", "   ")));
        when(sourceMapper.findAllByType("STANDARD_DOC")).thenReturn(List.of());

        StandardIndexSyncService.SyncReport report = service.sync();

        verify(knowledgeService, never()).indexStandard(any());
        assertThat(report.getSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SYNC-007 标准未生成切片时应跳过且不得虚报已索引")
    void skipsStandardThatProducesNoChunks() {
        when(standardMapper.findPublished())
                .thenReturn(List.of(standard(1L, "只有标题的标准", "# 参数标准")));
        when(sourceMapper.findAllByType("STANDARD_DOC")).thenReturn(List.of());
        when(knowledgeService.indexStandard(any(ParameterStandard.class))).thenReturn(importResult(0));

        StandardIndexSyncService.SyncReport report = service.sync();

        assertThat(report.getIndexed()).isZero();
        assertThat(report.getSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SYNC-008 零切片标准应在内容哈希短路前清理历史来源")
    void removesUnindexableStandardBeforeHashShortcut() {
        ParameterStandard std = standard(1L, "只有标题的标准", "# 参数标准");
        when(standardMapper.findPublished()).thenReturn(List.of(std));
        when(sourceMapper.findAllByType("STANDARD_DOC"))
                .thenReturn(List.of(indexed(1L, std.getTitle(), service.hashOf(std))));
        when(knowledgeService.removeStandardIfUnindexable(std)).thenReturn(true);

        StandardIndexSyncService.SyncReport report = service.sync();

        verify(knowledgeService).removeStandardIfUnindexable(std);
        verify(knowledgeService, never()).indexStandard(any());
        assertThat(report.getSkipped()).isEqualTo(1);
    }

    private KnowledgeService.ImportResult importResult(int chunks) {
        KnowledgeService.ImportResult result = new KnowledgeService.ImportResult();
        result.setChunkCount(chunks);
        return result;
    }
}
