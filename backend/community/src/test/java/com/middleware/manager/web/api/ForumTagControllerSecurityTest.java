package com.middleware.manager.web.api;

import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.GatewayAuthenticationToken;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ForumTagManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ForumTagControllerSecurityTest {

    @Test
    @DisplayName("TC-06 普通用户不可直接访问管理后台论坛标签接口")
    void regularUserCannotAccessAdminTags() {
        ForumTagManagementService service = mock(ForumTagManagementService.class);
        AdminForumTagController controller = new AdminForumTagController(service, new PermissionService());
        GatewayAuthenticationToken user = GatewayAuthenticationToken.authenticated(
                "user-a", "用户A", List.of("ROLE_USER"), "中间件", false);

        assertThrows(ForbiddenException.class, () -> controller.list(user));
    }
}
