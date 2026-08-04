package com.middleware.manager.wiki.web;

import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.LintResultMapper;
import com.middleware.manager.wiki.repository.WikiAuditLogMapper;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiPagePermissionMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import com.middleware.manager.wiki.service.LinkResolver;
import com.middleware.manager.wiki.service.LintAgent;
import com.middleware.manager.wiki.service.PageDraftService;
import com.middleware.manager.wiki.service.WikiExportService;
import com.middleware.manager.wiki.service.WikiGraphService;
import com.middleware.manager.wiki.service.WikiImportService;
import com.middleware.manager.wiki.service.WikiPermissionService;
import com.middleware.manager.wiki.web.dto.WikiPageSaveResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 建边失败必须让用户知道，并提供补救手段。
 * <p>此前 Controller 吞掉异常只写日志：用户以为交叉引用已生效，实际图谱与图扩展
 * 检索都少了边，且没有任何重试入口。保存本身仍应成功——页面内容不能因为建边失败
 * 而丢失，但失败必须可见、可补救。
 */
class WikiLinkFailureVisibilityTest {

    @Mock private WikiPageMapper pageMapper;
    @Mock private WikiLinkMapper linkMapper;
    @Mock private WikiSourceMapper sourceMapper;
    @Mock private WikiExportService exportService;
    @Mock private WikiImportService importService;
    @Mock private WikiGraphService graphService;
    @Mock private PageDraftService pageDraftService;
    @Mock private LinkResolver linkResolver;
    @Mock private AdminAccountMapper adminAccountMapper;
    @Mock private WikiAuditLogMapper auditLogMapper;
    @Mock private LintAgent lintAgent;
    @Mock private LintResultMapper lintResultMapper;
    @Mock private WikiPermissionService wikiPermissionService;
    @Mock private WikiPagePermissionMapper pagePermissionMapper;
    @Mock private VectorStore vectorStore;
    @Mock private Authentication authentication;
    @Mock private HttpServletRequest request;

    private WikiController controller;
    private String runId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runId = "KBV12V-" + UUID.randomUUID().toString().substring(0, 8);
        controller = new WikiController(
                pageMapper, linkMapper, sourceMapper,
                exportService, importService, graphService, pageDraftService, linkResolver,
                Collections.emptyList(), adminAccountMapper, auditLogMapper,
                lintAgent, lintResultMapper, wikiPermissionService, pagePermissionMapper,
                vectorStore);
        when(wikiPermissionService.getManagedCategory(authentication)).thenReturn("数据库");
    }

    private WikiPage page() {
        WikiPage p = new WikiPage();
        p.setId(1L);
        p.setTitle(runId + "-经验页");
        p.setContent("参考 [[某页面]]");
        p.setPageType("EXPERIENCE");
        p.setCategory("数据库");
        return p;
    }

    @Test
    @DisplayName("TC-LINK-012 建边失败时保存仍成功，但响应必须带出警告")
    void saveSucceedsButSurfacesWarning() {
        doThrow(new RuntimeException("数据库连接抖动")).when(linkResolver).resolveLinks(anyList());

        ResponseEntity<WikiPageSaveResponse> response =
                controller.createPage(page(), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getPage()).isNotNull();
        assertThat(response.getBody().getLinkWarning()).isNotBlank();
        verify(pageMapper).insert(any(WikiPage.class));
    }

    @Test
    @DisplayName("TC-LINK-013 建边成功时不应产生噪音警告")
    void successfulSaveHasNoWarning() {
        when(linkResolver.resolveLinks(anyList())).thenReturn(1);

        ResponseEntity<WikiPageSaveResponse> response =
                controller.createPage(page(), authentication, request);

        assertThat(response.getBody().getLinkWarning()).isNull();
    }

    @Test
    @DisplayName("TC-LINK-014 更新页面建边失败同样要带出警告")
    void updateSurfacesWarning() {
        when(pageMapper.findById(1L)).thenReturn(page());
        when(wikiPermissionService.isAdmin(authentication)).thenReturn(true);
        doThrow(new RuntimeException("数据库连接抖动")).when(linkResolver).resolveLinks(anyList());

        ResponseEntity<WikiPageSaveResponse> response =
                controller.updatePage(1L, page(), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getLinkWarning()).isNotBlank();
    }

    @Test
    @DisplayName("TC-LINK-015 应提供重建关联的补救入口，成功后返回本次建边数")
    void relinkEndpointRebuildsEdges() {
        WikiPage p = page();
        when(pageMapper.findById(1L)).thenReturn(p);
        when(linkResolver.resolveLinks(List.of(p))).thenReturn(2);

        ResponseEntity<WikiPageSaveResponse> response =
                controller.relinkPage(1L, authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getLinkWarning()).isNull();
        assertThat(response.getBody().getLinksCreated()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-LINK-016 重建关联再次失败时应返回警告而非 500")
    void relinkFailureReturnsWarning() {
        WikiPage p = page();
        when(pageMapper.findById(1L)).thenReturn(p);
        doThrow(new RuntimeException("仍然不可用")).when(linkResolver).resolveLinks(anyList());

        ResponseEntity<WikiPageSaveResponse> response =
                controller.relinkPage(1L, authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getLinkWarning()).isNotBlank();
    }

    @Test
    @DisplayName("TC-LINK-017 无管理权限不得调用重建关联")
    void relinkRequiresPermission() {
        when(wikiPermissionService.isAdmin(authentication)).thenReturn(false);
        when(wikiPermissionService.getManagedCategory(authentication)).thenReturn(null);

        ResponseEntity<WikiPageSaveResponse> response =
                controller.relinkPage(1L, authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
