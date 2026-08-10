package com.middleware.manager.web.api;

import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.GatewayAuthenticationToken;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ForumTagManagementService;
import com.middleware.manager.web.api.dto.ForumTagRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumTagApiControllerSecurityTest {
    @Mock
    private ForumTagManagementService tagService;

    private AdminForumTagApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminForumTagApiController(tagService, new PermissionService());
    }

    @Test
    @DisplayName("TC-FORUM-TAG-003 (TC-03) 普通用户不可查看管理后台标签")
    void ordinaryUserCannotListTags() {
        GatewayAuthenticationToken ordinary = GatewayAuthenticationToken.authenticated(
                "user", "普通用户", List.of("ROLE_USER"), null, false);

        assertThrows(ForbiddenException.class, () -> controller.list(ordinary));
        verify(tagService, never()).listAll();
    }

    @Test
    @DisplayName("TC-FORUM-TAG-008 (TC-08) 组管理员不可越权编辑或删除其他组标签")
    void categoryAdminCannotMutateAnotherCategory() {
        GatewayAuthenticationToken categoryAdmin = GatewayAuthenticationToken.authenticated(
                "middleware-admin", "中间件管理员", List.of("ROLE_MIDDLEWARE_ADMIN"), "中间件", true);
        ForumTag anotherCategory = new ForumTag();
        anotherCategory.setId(8L);
        anotherCategory.setCategory("数据库");
        when(tagService.get(8L)).thenReturn(anotherCategory);

        assertThrows(ForbiddenException.class,
                () -> controller.rename(8L, new ForumTagRequest("越权修改", "数据库"), categoryAdmin));
        assertThrows(ForbiddenException.class, () -> controller.delete(8L, categoryAdmin));

        verify(tagService, never()).rename(8L, "越权修改");
        verify(tagService, never()).delete(8L);
    }

    @Test
    @DisplayName("TC-FORUM-TAG-009 (TC-03) 系统管理员新增标签时由后端自动归入未分组")
    void systemAdminCreateUsesDefaultCategory() {
        GatewayAuthenticationToken systemAdmin = GatewayAuthenticationToken.authenticated(
                "admin", "系统管理员", List.of("ROLE_SYS_ADMIN"), null, false);
        ForumTag created = new ForumTag();
        created.setName("容量规划");
        created.setCategory("未分组");
        when(tagService.create("容量规划", "未分组", "admin")).thenReturn(created);

        controller.create(new ForumTagRequest("容量规划", "数据库"), systemAdmin);

        verify(tagService).create("容量规划", "未分组", "admin");
    }

    @Test
    @DisplayName("TC-FORUM-TAG-010 (TC-03) 组管理员新增标签时由后端使用管理上下文")
    void categoryAdminCreateUsesManagedCategory() {
        GatewayAuthenticationToken categoryAdmin = GatewayAuthenticationToken.authenticated(
                "middleware-admin", "中间件管理员", List.of("ROLE_MIDDLEWARE_ADMIN"), "中间件", true);
        ForumTag created = new ForumTag();
        created.setName("容量规划");
        created.setCategory("中间件");
        when(tagService.create("容量规划", "中间件", "middleware-admin")).thenReturn(created);

        controller.create(new ForumTagRequest("容量规划", "数据库"), categoryAdmin);

        verify(tagService).create("容量规划", "中间件", "middleware-admin");
    }
}
