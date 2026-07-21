package com.middleware.manager.web.api;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import com.middleware.manager.service.TokenService;
import com.middleware.manager.web.api.dto.AuthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthApiController {

    private final AdminAccountService adminAccountService;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final TokenService tokenService;

    public AuthApiController(AdminAccountService adminAccountService, PasswordEncoder passwordEncoder,
                             RoleService roleService, TokenService tokenService) {
        this.adminAccountService = adminAccountService;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Credentials credentials = parseBasicCredentials(authorization);
        log.debug("login attempt: username={}", credentials.username);
        UserDetails user;
        try {
            user = adminAccountService.loadUserByUsername(credentials.username);
        } catch (Exception e) {
            log.warn("login failed: username={} user_not_found", credentials.username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(credentials.password, user.getPassword())) {
            log.warn("login failed: username={} password_mismatch", credentials.username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        String authority = user.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority()).orElse("ROLE_SYS_ADMIN");
        RoleEntity role = roleService.getByAuthority(authority);
        String roleName = role != null ? role.getDisplayName() : "系统管理员";

        // 生成 token
        String token = tokenService.createToken(credentials.username);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(2);

        log.debug("login success: username={} role={}", credentials.username, roleName);
        return new AuthResponse(user.getUsername(),
                adminAccountService.getDisplayNameByUsername(user.getUsername()),
                roleName, token, expiresAt);
    }

    @GetMapping("/me")
    public AuthResponse currentUser(Authentication authentication) {
        RoleEntity role = authentication.getAuthorities().stream()
                .map(authority -> roleService.getByAuthority(authority.getAuthority()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new AuthResponse(authentication.getName(),
                adminAccountService.getDisplayNameByUsername(authentication.getName()),
                role != null ? role.getDisplayName() : "系统管理员");
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            tokenService.deleteToken(token);
        }
    }

    private Credentials parseBasicCredentials(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing credentials");
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            return new Credentials(decoded.substring(0, separator), decoded.substring(separator + 1));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    private static class Credentials {
        private final String username;
        private final String password;

        private Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
