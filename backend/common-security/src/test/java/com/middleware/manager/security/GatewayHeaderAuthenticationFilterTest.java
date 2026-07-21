package com.middleware.manager.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class GatewayHeaderAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-only-gateway-signing-secret";

    private GatewaySignatureService signatureService;
    private GatewayHeaderAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        signatureService = new GatewaySignatureService(TEST_SECRET);
        filter = new GatewayHeaderAuthenticationFilter(signatureService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC-SECURITY-001 伪造身份头无签名时保持未认证")
    void forgedHeadersWithoutSignatureRemainUnauthenticated() throws Exception {
        MockHttpServletRequest request = identityRequest(false, null);

        filter.doFilter(request, new MockHttpServletResponse(), passthroughChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("TC-SECURITY-002 伪造身份头签名错误时保持未认证")
    void forgedHeadersWithWrongSignatureRemainUnauthenticated() throws Exception {
        MockHttpServletRequest request = identityRequest(false, "wrong-signature");

        filter.doFilter(request, new MockHttpServletResponse(), passthroughChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("TC-SECURITY-003 正确签名身份头构建认证上下文")
    void correctlySignedHeadersAuthenticate() throws Exception {
        String signature = signatureService.signIdentityHeaders(
                "alice", "Alice", "ROLE_MIDDLEWARE_ADMIN", "中间件", "true");
        MockHttpServletRequest request = identityRequest(true, signature);

        filter.doFilter(request, new MockHttpServletResponse(), passthroughChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOfSatisfying(GatewayAuthenticationToken.class, authentication -> {
                    assertThat(authentication.getName()).isEqualTo("alice");
                    assertThat(authentication.getDisplayName()).isEqualTo("Alice");
                    assertThat(authentication.getCategory()).isEqualTo("中间件");
                    assertThat(authentication.isCategoryAdmin()).isTrue();
                    assertThat(authentication.getAuthorities())
                            .extracting("authority")
                            .containsExactly("ROLE_MIDDLEWARE_ADMIN");
                });
    }

    @Test
    @DisplayName("TC-SECURITY-004 签名后篡改角色时保持未认证")
    void roleTamperingAfterSigningRemainsUnauthenticated() throws Exception {
        String signature = signatureService.signIdentityHeaders(
                "alice", "Alice", "ROLE_DEV_MGR", "", "false");
        MockHttpServletRequest request = identityRequest(false, signature);

        filter.doFilter(request, new MockHttpServletResponse(), passthroughChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("TC-SECURITY-005 角色与岗位权限完全基于签名身份上下文")
    void permissionServiceUsesSignedRoleAndCategoryContext() {
        PermissionService permissionService = new PermissionService();
        GatewayAuthenticationToken middlewareAdmin = GatewayAuthenticationToken.authenticated(
                "alice", "Alice", List.of("ROLE_MIDDLEWARE_ADMIN"), "中间件", true);
        GatewayAuthenticationToken middlewareManager = GatewayAuthenticationToken.authenticated(
                "bob", "Bob", List.of("ROLE_MIDDLEWARE_MGR"), "中间件", false);
        GatewayAuthenticationToken systemAdmin = GatewayAuthenticationToken.authenticated(
                "root", "Root", List.of("ROLE_SYS_ADMIN"), null, false);

        assertThat(permissionService.isCategoryAdmin(middlewareAdmin)).isTrue();
        assertThat(permissionService.canManageCategory(middlewareAdmin, "中间件")).isTrue();
        assertThat(permissionService.canReview(middlewareAdmin, "中间件")).isTrue();
        assertThat(permissionService.canManageCategory(middlewareAdmin, "数据库")).isFalse();
        assertThat(permissionService.canReview(middlewareManager, "中间件")).isFalse();
        assertThat(permissionService.canManageCategory(middlewareManager, "中间件")).isTrue();
        assertThat(permissionService.isAdmin(systemAdmin)).isTrue();
        assertThat(permissionService.canManageCategory(systemAdmin, "数据库")).isTrue();
        assertThat(permissionService.canReview(systemAdmin, "安全")).isTrue();
    }

    private MockHttpServletRequest identityRequest(boolean categoryAdmin, String signature) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/releases");
        request.addHeader(GatewayIdentityHeaders.USER, "alice");
        request.addHeader(GatewayIdentityHeaders.DISPLAY_NAME, "Alice");
        request.addHeader(GatewayIdentityHeaders.ROLES, "ROLE_MIDDLEWARE_ADMIN");
        request.addHeader(GatewayIdentityHeaders.CATEGORY, "中间件");
        request.addHeader(GatewayIdentityHeaders.CATEGORY_ADMIN, Boolean.toString(categoryAdmin));
        if (signature != null) {
            request.addHeader(GatewayIdentityHeaders.SIGNATURE, signature);
        }
        return request;
    }

    private FilterChain passthroughChain() {
        return (request, response) -> { };
    }
}
