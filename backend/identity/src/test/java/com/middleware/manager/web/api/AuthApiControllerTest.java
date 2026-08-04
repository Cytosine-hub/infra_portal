package com.middleware.manager.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import com.middleware.manager.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthApiControllerTest {

    @Mock
    private AdminAccountService adminAccountService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleService roleService;

    @Mock
    private TokenService tokenService;

    private AuthApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthApiController(adminAccountService, passwordEncoder, roleService, tokenService);
    }

    @Test
    @DisplayName("TC-IDENTITY-005 登录响应的过期时间必须携带时区偏移")
    void loginExpiryContainsUtcOffset() {
        when(adminAccountService.loadUserByUsername("sysadmin")).thenReturn(User.withUsername("sysadmin")
                .password("stored-password").authorities("ROLE_SYS_ADMIN").build());
        when(passwordEncoder.matches("password-hash", "stored-password")).thenReturn(true);
        RoleEntity role = new RoleEntity();
        role.setDisplayName("系统管理员");
        when(roleService.getByAuthority("ROLE_SYS_ADMIN")).thenReturn(role);
        when(tokenService.createToken("sysadmin")).thenReturn("test-token");
        when(adminAccountService.getDisplayNameByUsername("sysadmin")).thenReturn("系统管理员");

        String credentials = Base64.getEncoder().encodeToString(
                "sysadmin:password-hash".getBytes(StandardCharsets.UTF_8));
        var response = controller.login("Basic " + credentials);

        assertThat(response.getExpiresAt().toString()).matches(".*(?:Z|[+-]\\d{2}:\\d{2})$");
    }
}
