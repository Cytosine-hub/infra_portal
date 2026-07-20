package com.middleware.manager.wiki.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiPagePermission;
import com.middleware.manager.wiki.repository.WikiPagePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WikiPermissionServiceTest {

    @Mock
    private WikiPagePermissionMapper permissionMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private Authentication authentication;

    private WikiPermissionService wikiPermissionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        wikiPermissionService = new WikiPermissionService(permissionMapper, permissionService, objectMapper);
    }

    @Nested
    @DisplayName("canView")
    class CanView {

        @Test
        @DisplayName("null 页面返回 false")
        void nullPageReturnsFalse() {
            assertFalse(wikiPermissionService.canView(authentication, null));
        }

        @Test
        @DisplayName("管理员可查看所有页面")
        void adminCanViewAll() {
            when(permissionService.isAdmin(authentication)).thenReturn(true);
            WikiPage page = createPage(1L, "中间件");

            assertTrue(wikiPermissionService.canView(authentication, page));
        }

        @Test
        @DisplayName("无权限覆盖且无分类的页面对所有人可见")
        void noOverrideNoCategoryVisible() {
            when(permissionService.isAdmin(authentication)).thenReturn(false);
            when(permissionMapper.findByPageId(1L)).thenReturn(null);

            WikiPage page = createPage(1L, null);
            assertTrue(wikiPermissionService.canView(authentication, page));
        }

        @Test
        @DisplayName("HIDDEN 页面对非管理员不可见")
        void hiddenPageNotVisible() {
            when(permissionService.isAdmin(authentication)).thenReturn(false);

            WikiPagePermission perm = new WikiPagePermission();
            perm.setPermissionType("HIDDEN");
            when(permissionMapper.findByPageId(1L)).thenReturn(perm);

            WikiPage page = createPage(1L, "中间件");
            assertFalse(wikiPermissionService.canView(authentication, page));
        }

        @Test
        @DisplayName("RESTRICTED 页面仅对目标角色可见")
        void restrictedPageVisibleToTargetRoles() {
            when(permissionService.isAdmin(authentication)).thenReturn(false);

            WikiPagePermission perm = new WikiPagePermission();
            perm.setPermissionType("RESTRICTED");
            perm.setTargetRoles("[\"ROLE_MIDDLEWARE_ADMIN\"]");
            when(permissionMapper.findByPageId(1L)).thenReturn(perm);

            WikiPage page = createPage(1L, "中间件");

            // 用户有目标角色
            doReturn(Collections.<GrantedAuthority>singletonList(new SimpleGrantedAuthority("ROLE_MIDDLEWARE_ADMIN")))
                    .when(authentication).getAuthorities();
            assertTrue(wikiPermissionService.canView(authentication, page));

            // 用户没有目标角色
            doReturn(Collections.<GrantedAuthority>singletonList(new SimpleGrantedAuthority("ROLE_DATABASE_ADMIN")))
                    .when(authentication).getAuthorities();
            assertFalse(wikiPermissionService.canView(authentication, page));
        }

        @Test
        @DisplayName("VISIBLE 页面回退到分类权限检查")
        void visiblePageFallsBackToCategory() {
            when(permissionService.isAdmin(authentication)).thenReturn(false);

            WikiPagePermission perm = new WikiPagePermission();
            perm.setPermissionType("VISIBLE");
            when(permissionMapper.findByPageId(1L)).thenReturn(perm);

            WikiPage page = createPage(1L, "中间件");

            when(permissionService.canManageCategory(authentication, "中间件")).thenReturn(true);
            assertTrue(wikiPermissionService.canView(authentication, page));

            when(permissionService.canManageCategory(authentication, "中间件")).thenReturn(false);
            assertFalse(wikiPermissionService.canView(authentication, page));
        }
    }

    @Nested
    @DisplayName("filterVisiblePages")
    class FilterVisiblePages {

        @Test
        @DisplayName("null 输入返回空列表")
        void nullInputReturnsEmpty() {
            List<WikiPage> result = wikiPermissionService.filterVisiblePages(authentication, null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("管理员看到所有页面")
        void adminSeesAll() {
            when(permissionService.isAdmin(authentication)).thenReturn(true);
            List<WikiPage> pages = Arrays.asList(createPage(1L, "A"), createPage(2L, "B"));

            List<WikiPage> result = wikiPermissionService.filterVisiblePages(authentication, pages);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("非管理员只看到有权限的页面")
        void nonAdminSeesOnlyPermitted() {
            when(permissionService.isAdmin(authentication)).thenReturn(false);

            WikiPagePermission hidden = new WikiPagePermission();
            hidden.setPermissionType("HIDDEN");

            when(permissionMapper.findByPageId(1L)).thenReturn(null);  // 无覆盖，可见
            when(permissionMapper.findByPageId(2L)).thenReturn(hidden); // HIDDEN，不可见

            WikiPage page1 = createPage(1L, null);
            WikiPage page2 = createPage(2L, "中间件");
            List<WikiPage> pages = Arrays.asList(page1, page2);

            List<WikiPage> result = wikiPermissionService.filterVisiblePages(authentication, pages);
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
        }
    }

    @Nested
    @DisplayName("setPagePermission")
    class SetPagePermission {

        @Test
        @DisplayName("新权限插入")
        void insertNewPermission() {
            when(permissionMapper.findByPageId(1L)).thenReturn(null);

            WikiPagePermission result = wikiPermissionService.setPagePermission(
                    1L, "HIDDEN", null, 10L);

            assertNotNull(result);
            assertEquals(1L, result.getPageId());
            assertEquals("HIDDEN", result.getPermissionType());
            assertEquals(10L, result.getCreatedBy());
            verify(permissionMapper).insert(any(WikiPagePermission.class));
            verify(permissionMapper, never()).update(any());
        }

        @Test
        @DisplayName("已有权限更新")
        void updateExistingPermission() {
            WikiPagePermission existing = new WikiPagePermission();
            existing.setId(100L);
            existing.setPageId(1L);
            existing.setPermissionType("VISIBLE");
            when(permissionMapper.findByPageId(1L)).thenReturn(existing);

            WikiPagePermission result = wikiPermissionService.setPagePermission(
                    1L, "RESTRICTED", "[\"ROLE_ADMIN\"]", 10L);

            assertEquals(100L, result.getId());
            assertEquals("RESTRICTED", result.getPermissionType());
            verify(permissionMapper).update(any(WikiPagePermission.class));
            verify(permissionMapper, never()).insert(any());
        }
    }

    private WikiPage createPage(Long id, String category) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setTitle("Page " + id);
        page.setCategory(category);
        page.setStatus("ACTIVE");
        return page;
    }
}
