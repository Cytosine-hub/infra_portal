package com.middleware.manager.wiki.web;

import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.knowledge.store.VectorStore;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 经验页面的人工创作路径。LLM 编译流水线下线后，页面只能由人来写，
 * 因此新建端点与发布权限校验是这条路径的关键保障。
 */
class WikiPageAuthoringTest {

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

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new WikiController(
                pageMapper, linkMapper, sourceMapper,
                exportService, importService, graphService, pageDraftService, linkResolver,
                Collections.emptyList(), adminAccountMapper, auditLogMapper,
                lintAgent, lintResultMapper, wikiPermissionService, pagePermissionMapper,
                vectorStore);
    }

    private WikiPage page(String title, String status) {
        WikiPage p = new WikiPage();
        p.setId(1L);
        p.setTitle(title);
        p.setContent("正文内容");
        p.setPageType("EXPERIENCE");
        p.setCategory("数据库");
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("TC-WIKI-AUTH-001 管理岗可新建经验页面，默认落为草稿")
    void managerCanCreatePage() {
        when(wikiPermissionService.getManagedCategory(authentication)).thenReturn("数据库");

        ResponseEntity<WikiPage> response =
                controller.createPage(page("主从延迟处理", null), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("DRAFT");
        verify(pageMapper).insert(any(WikiPage.class));
    }

    @Test
    @DisplayName("TC-WIKI-AUTH-002 普通用户无管理分类时不得新建页面")
    void plainUserCannotCreatePage() {
        when(wikiPermissionService.isAdmin(authentication)).thenReturn(false);
        when(wikiPermissionService.getManagedCategory(authentication)).thenReturn(null);

        ResponseEntity<WikiPage> response =
                controller.createPage(page("越权页面", null), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(pageMapper, never()).insert(any(WikiPage.class));
    }

    @Test
    @DisplayName("TC-WIKI-AUTH-003 标题为空应拒绝创建")
    void blankTitleRejected() {
        when(wikiPermissionService.getManagedCategory(authentication)).thenReturn("数据库");
        WikiPage blank = page("   ", null);

        ResponseEntity<WikiPage> response = controller.createPage(blank, authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(pageMapper, never()).insert(any(WikiPage.class));
    }

    @Test
    @DisplayName("TC-WIKI-AUTH-004 无审核权限的用户不得把草稿改为已发布")
    void nonReviewerCannotPublish() {
        when(pageMapper.findById(1L)).thenReturn(page("待发布", "DRAFT"));
        when(wikiPermissionService.isAdmin(authentication)).thenReturn(false);

        ResponseEntity<WikiPage> response =
                controller.updatePage(1L, page("待发布", "ACTIVE"), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(pageMapper, never()).update(any(WikiPage.class));
    }

    @Test
    @DisplayName("TC-WIKI-AUTH-005 管理员可以发布并被记为审核人")
    void adminCanPublish() {
        when(pageMapper.findById(1L)).thenReturn(page("待发布", "DRAFT"));
        when(wikiPermissionService.isAdmin(authentication)).thenReturn(true);

        ResponseEntity<WikiPage> response =
                controller.updatePage(1L, page("待发布", "ACTIVE"), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(pageMapper).update(any(WikiPage.class));
    }

    @Test
    @DisplayName("TC-WIKI-AUTH-006 不涉及发布的普通编辑不受审核权限限制")
    void ordinaryEditNotBlocked() {
        when(pageMapper.findById(1L)).thenReturn(page("草稿", "DRAFT"));
        when(wikiPermissionService.isAdmin(authentication)).thenReturn(false);

        ResponseEntity<WikiPage> response =
                controller.updatePage(1L, page("草稿改标题", "DRAFT"), authentication, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(pageMapper).update(any(WikiPage.class));
    }
}
