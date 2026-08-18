package com.middleware.manager.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.exception.UnauthorizedException;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    @Test
    @DisplayName("TC-IDENTITY-006 未知账号登录应返回统一认证失败提示")
    void unknownUsernameReturnsLoginFailed() {
        when(adminAccountService.loadUserByUsername("missing"))
                .thenThrow(new UsernameNotFoundException("not found"));

        assertLoginFailed(basicCredentials("missing", "password-hash"));
    }

    @Test
    @DisplayName("TC-IDENTITY-007 密码错误登录应返回统一认证失败提示")
    void wrongPasswordReturnsLoginFailed() {
        when(adminAccountService.loadUserByUsername("sysadmin")).thenReturn(User.withUsername("sysadmin")
                .password("stored-password").authorities("ROLE_SYS_ADMIN").build());
        when(passwordEncoder.matches("wrong-password-hash", "stored-password")).thenReturn(false);

        assertLoginFailed(basicCredentials("sysadmin", "wrong-password-hash"));
    }

    @Test
    @DisplayName("TC-IDENTITY-008 登录凭据格式错误应返回统一认证失败提示")
    void malformedCredentialsReturnLoginFailed() {
        assertLoginFailed("Basic invalid-base64");
    }

    @Test
    @DisplayName("TC-IDENTITY-009 账号查询系统异常不得伪装成认证失败")
    void accountLookupFailureRemainsSystemFailure() {
        when(adminAccountService.loadUserByUsername("sysadmin"))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> controller.login(basicCredentials("sysadmin", "password-hash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private void assertLoginFailed(String authorization) {
        assertThatThrownBy(() -> controller.login(authorization))
                .isInstanceOfSatisfying(UnauthorizedException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
                    assertThat(exception.getMessage()).isEqualTo(ErrorMessages.LOGIN_FAILED);
                });
    }

    private String basicCredentials(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
