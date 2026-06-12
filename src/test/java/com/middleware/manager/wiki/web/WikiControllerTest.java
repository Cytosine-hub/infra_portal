package com.middleware.manager.wiki.web;

import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.*;
import com.middleware.manager.wiki.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WikiControllerTest {

    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiLinkMapper linkMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private IngestAgent ingestAgent;
    @Mock private WikiExportService exportService;
    @Mock private WikiImportService importService;
    @Mock private WikiGraphService graphService;
    @Mock private AdminAccountMapper adminAccountMapper;
    @Mock private WikiAuditLogMapper auditLogMapper;
    @Mock private LintAgent lintAgent;
    @Mock private LintResultMapper lintResultMapper;
    @Mock private WikiPermissionService wikiPermissionService;
    @Mock private WikiPagePermissionMapper pagePermissionMapper;
    @Mock private IngestTaskService taskService;
    @Mock private IngestTaskMapper taskMapper;
    @Mock private WikiIngestLogMapper ingestLogMapper;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private WikiSearchService wikiSearchService;

    private WikiController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new WikiController(
                pageMapper, linkMapper, sourceMapper, ingestAgent,
                exportService, importService, graphService,
                Collections.emptyList(), adminAccountMapper, auditLogMapper,
                lintAgent, lintResultMapper, wikiPermissionService, pagePermissionMapper,
                taskService, taskMapper, ingestLogMapper, embeddingService, vectorStore);
    }

    @Nested
    @DisplayName("deletePage 级联删除")
    class DeletePageCascade {

        @Test
        @DisplayName("删除页面时同步清理关联数据")
        void deletePageCleansUpRelatedData() {
            ResponseEntity<Void> response = controller.deletePage(42L);

            assertEquals(200, response.getStatusCode().value());

            // 验证级联删除
            verify(linkMapper).deleteByPageId(42L);
            verify(pagePermissionMapper).deleteByPageId(42L);
            verify(vectorStore).delete("wiki_42");
            verify(pageMapper).deleteById(42L);
        }

        @Test
        @DisplayName("向量删除失败不影响页面删除")
        void vectorDeleteFailureDoesNotBlockPageDelete() {
            doThrow(new RuntimeException("Vector store unavailable"))
                    .when(vectorStore).delete("wiki_42");

            ResponseEntity<Void> response = controller.deletePage(42L);

            assertEquals(200, response.getStatusCode().value());
            verify(pageMapper).deleteById(42L);
        }
    }

    @Nested
    @DisplayName("getPage")
    class GetPage {

        @Test
        @DisplayName("页面存在返回 200")
        void pageFound() {
            WikiPage page = new WikiPage();
            page.setId(1L);
            when(pageMapper.findById(1L)).thenReturn(page);

            ResponseEntity<WikiPage> response = controller.getPage(1L);

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(1L, response.getBody().getId());
        }

        @Test
        @DisplayName("页面不存在返回 404")
        void pageNotFound() {
            when(pageMapper.findById(999L)).thenReturn(null);

            ResponseEntity<WikiPage> response = controller.getPage(999L);

            assertEquals(404, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("listPages")
    class ListPages {

        @Test
        @DisplayName("无参数时返回全部页面")
        void listAll() {
            when(pageMapper.findAllExcludingContent()).thenReturn(Collections.emptyList());

            controller.listPages(null, null, null);

            verify(pageMapper).findAllExcludingContent();
        }

        @Test
        @DisplayName("按分类过滤")
        void filterByCategory() {
            when(pageMapper.findByCategoryExcludingContent("中间件")).thenReturn(Collections.emptyList());

            controller.listPages("中间件", null, null);

            verify(pageMapper).findByCategoryExcludingContent("中间件");
        }

        @Test
        @DisplayName("按状态过滤")
        void filterByStatus() {
            when(pageMapper.findByStatusExcludingContent("ACTIVE")).thenReturn(Collections.emptyList());

            controller.listPages(null, null, "ACTIVE");

            verify(pageMapper).findByStatusExcludingContent("ACTIVE");
        }
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("统计数据包含所有计数字段")
        void statsContainsAllFields() {
            when(pageMapper.findAllExcludingContent()).thenReturn(Collections.emptyList());
            when(pageMapper.countByStatus("ACTIVE")).thenReturn(5);
            when(pageMapper.countByStatus("DRAFT")).thenReturn(3);
            when(pageMapper.countByStatus("CONTRADICTED")).thenReturn(1);
            when(pageMapper.countByStatus("STALE")).thenReturn(0);
            when(sourceMapper.findAll()).thenReturn(Collections.emptyList());
            when(sourceMapper.findByIngested(false)).thenReturn(Collections.emptyList());

            var stats = controller.getStats();

            assertEquals(0, stats.get("total_pages"));
            assertEquals(5, stats.get("active_pages"));
            assertEquals(3, stats.get("draft_pages"));
            assertEquals(1, stats.get("contradicted_pages"));
            assertEquals(0, stats.get("stale_pages"));
            assertEquals(0, stats.get("total_sources"));
            assertEquals(0, stats.get("uningested_sources"));
        }
    }
}
