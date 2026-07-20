package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

class WikiGraphServiceTest {

    @Mock
    private WikiPageMapper pageMapper;
    @Mock
    private WikiLinkMapper linkMapper;
    @Mock
    private WikiPermissionService permissionService;

    private WikiGraphService graphService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        graphService = new WikiGraphService(pageMapper, linkMapper, permissionService);
        ReflectionTestUtils.setField(graphService, "maxNodes", 1000);
    }

    @Test
    @DisplayName("登录用户图谱包含有权限的草稿页面")
    void authenticatedGraphIncludesVisibleDraftPages() {
        WikiPage entity = page(1L, "TongWeb", "ENTITY", "DRAFT");
        WikiPage concept = page(2L, "集群管理", "CONCEPT", "DRAFT");
        List<WikiPage> pages = List.of(entity, concept);
        TestingAuthenticationToken authentication = authentication();

        when(pageMapper.findAllExcludingContent()).thenReturn(pages);
        when(permissionService.filterVisiblePages(authentication, pages)).thenReturn(pages);
        when(linkMapper.findAll()).thenReturn(List.of(link(1L, 2L)));

        Map<String, Object> graph = graphService.buildGraph(authentication);

        assertEquals(2, ((List<?>) graph.get("nodes")).size());
        assertEquals(1, ((List<?>) graph.get("links")).size());
    }

    @Test
    @DisplayName("匿名图谱不展示草稿页面")
    void publicGraphExcludesDraftPages() {
        when(pageMapper.findAllExcludingContent()).thenReturn(List.of(page(1L, "TongWeb", "ENTITY", "DRAFT")));
        when(linkMapper.findAll()).thenReturn(List.of());

        Map<String, Object> graph = graphService.buildGraph(null);

        assertEquals(0, ((List<?>) graph.get("nodes")).size());
        assertEquals(0, ((List<?>) graph.get("links")).size());
    }

    @Test
    @DisplayName("图谱社区按分类和软件稳定聚类")
    void graphCommunitiesGroupByCategoryAndSoftware() {
        WikiPage besInstall = page(1L, "BES 安装", "RUNBOOK", "ACTIVE", "中间件", "BES");
        WikiPage besMonitor = page(2L, "BES 监控", "RUNBOOK", "ACTIVE", "中间件", "BES");
        WikiPage mysqlInstall = page(3L, "MySQL 安装", "RUNBOOK", "ACTIVE", "数据库", "MySQL");

        when(pageMapper.findAllExcludingContent()).thenReturn(List.of(besInstall, besMonitor, mysqlInstall));
        when(linkMapper.findAll()).thenReturn(List.of());

        Map<String, Object> graph = graphService.buildGraph(null);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        Map<String, Object> besNode = findNode(nodes, 1L);
        Map<String, Object> besMonitorNode = findNode(nodes, 2L);
        Map<String, Object> mysqlNode = findNode(nodes, 3L);

        assertEquals(besNode.get("community"), besMonitorNode.get("community"));
        assertNotEquals(besNode.get("community"), mysqlNode.get("community"));
        assertEquals("BES (中间件)", besNode.get("communityName"));
        assertEquals(2, graph.get("communityCount"));

        List<Map<String, Object>> communityStats = (List<Map<String, Object>>) graph.get("communityStats");
        Map<String, Object> besStats = communityStats.stream()
                .filter(item -> "BES (中间件)".equals(item.get("name")))
                .findFirst()
                .orElseThrow();
        assertEquals(2, besStats.get("nodeCount"));
        assertEquals(1, besStats.get("edgeCount"));
    }

    private TestingAuthenticationToken authentication() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("mwadmin", "n/a", "ROLE_MIDDLEWARE_ADMIN");
        authentication.setAuthenticated(true);
        return authentication;
    }

    private WikiPage page(Long id, String title, String pageType, String status) {
        return page(id, title, pageType, status, "中间件", "TongWeb V7.0");
    }

    private WikiPage page(Long id, String title, String pageType, String status, String category, String software) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setTitle(title);
        page.setPageType(pageType);
        page.setStatus(status);
        page.setCategory(category);
        page.setSoftware(software);
        page.setSourceRefs("doc.pdf");
        return page;
    }

    private Map<String, Object> findNode(List<Map<String, Object>> nodes, Long pageId) {
        return nodes.stream()
                .filter(node -> pageId.equals(node.get("pageId")))
                .findFirst()
                .orElseThrow();
    }

    private WikiLink link(Long from, Long to) {
        WikiLink link = new WikiLink();
        link.setFromPageId(from);
        link.setToPageId(to);
        link.setLinkType("RELATED");
        link.setConfidence(BigDecimal.ONE);
        return link;
    }
}
