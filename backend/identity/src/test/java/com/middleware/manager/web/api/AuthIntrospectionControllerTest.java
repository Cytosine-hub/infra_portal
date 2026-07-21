package com.middleware.manager.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import com.middleware.manager.service.TokenService;
import com.middleware.manager.web.api.dto.AuthIntrospectionRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
class AuthIntrospectionControllerTest {

    private static final String TEST_SECRET = "test-only-gateway-signing-secret";

    @Mock
    private TokenService tokenService;

    @Mock
    private AdminAccountService adminAccountService;

    @Mock
    private RoleService roleService;

    @Mock
    private PermissionService permissionService;

    private GatewaySignatureService signatureService;
    private AuthIntrospectionController controller;

    @BeforeEach
    void setUp() {
        signatureService = new GatewaySignatureService(TEST_SECRET);
        controller = new AuthIntrospectionController(tokenService, adminAccountService,
                roleService, permissionService, signatureService);
    }

    @Test
    @DisplayName("TC-IDENTITY-001 有效 Token 返回用户角色与岗位并触发滑动续期校验")
    void validTokenReturnsIdentity() {
        when(tokenService.validateToken("valid-token")).thenReturn("alice");
        when(adminAccountService.loadUserByUsername("alice")).thenReturn(User.withUsername("alice")
                .password("unused").authorities("ROLE_MIDDLEWARE_ADMIN").build());
        when(adminAccountService.getDisplayNameByUsername("alice")).thenReturn("Alice");
        RoleEntity role = new RoleEntity();
        role.setAuthority("ROLE_MIDDLEWARE_ADMIN");
        role.setManagedCategory("中间件");
        role.setCategoryAdmin(true);
        when(roleService.getByAuthority("ROLE_MIDDLEWARE_ADMIN")).thenReturn(role);
        when(permissionService.getManagedCategory(org.mockito.ArgumentMatchers.any())).thenReturn("中间件");
        when(permissionService.isCategoryAdmin(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        var response = controller.introspect(
                new AuthIntrospectionRequest("valid-token"),
                signatureService.signIntrospectionToken("valid-token"));

        assertThat(response.valid()).isTrue();
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.roles()).containsExactly("ROLE_MIDDLEWARE_ADMIN");
        assertThat(response.category()).isEqualTo("中间件");
        assertThat(response.categoryAdmin()).isTrue();
    }

    @Test
    @DisplayName("TC-IDENTITY-002 过期 Token 返回 valid=false")
    void expiredTokenReturnsInvalid() {
        when(tokenService.validateToken("expired-token")).thenReturn(null);

        var response = controller.introspect(
                new AuthIntrospectionRequest("expired-token"),
                signatureService.signIntrospectionToken("expired-token"));

        assertThat(response.valid()).isFalse();
        assertThat(response.roles()).isEqualTo(List.of());
        verifyNoInteractions(adminAccountService, roleService, permissionService);
    }

    @Test
    @DisplayName("TC-IDENTITY-003 introspect 缺少网关签名时拒绝")
    void missingSignatureIsForbidden() {
        assertThatThrownBy(() -> controller.introspect(
                new AuthIntrospectionRequest("valid-token"), null))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(tokenService, adminAccountService, roleService, permissionService);
    }

    @Test
    @DisplayName("TC-IDENTITY-004 introspect 网关签名错误时拒绝")
    void wrongSignatureIsForbidden() {
        assertThatThrownBy(() -> controller.introspect(
                new AuthIntrospectionRequest("valid-token"), "wrong-signature"))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(tokenService, adminAccountService, roleService, permissionService);
    }
}
