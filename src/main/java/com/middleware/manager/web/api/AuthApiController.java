package com.middleware.manager.web.api;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import com.middleware.manager.web.api.dto.AuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthApiController.class);

    private final AdminAccountService adminAccountService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final RoleService roleService;

    public AuthApiController(AdminAccountService adminAccountService, PasswordEncoder passwordEncoder,
                             PermissionService permissionService, RoleService roleService) {
        this.adminAccountService = adminAccountService;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
        this.roleService = roleService;
    }

    @GetMapping("/login")
    public AuthResponse login(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Credentials credentials = parseBasicCredentials(authorization);
        LOG.info("login attempt: username={}", credentials.username);
        UserDetails user;
        try {
            user = adminAccountService.loadUserByUsername(credentials.username);
        } catch (Exception e) {
            LOG.warn("login failed: username={} user_not_found", credentials.username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(credentials.password, user.getPassword())) {
            LOG.warn("login failed: username={} password_mismatch (pwd_len={})", credentials.username, credentials.password.length());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String authority = user.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority()).orElse("ROLE_SYS_ADMIN");
        RoleEntity role = roleService.getByAuthority(authority);
        String roleName = role != null ? role.getDisplayName() : "系统管理员";
        LOG.info("login success: username={} role={}", credentials.username, roleName);
        return new AuthResponse(user.getUsername(), adminAccountService.getDisplayNameByUsername(user.getUsername()), roleName);
    }

    @GetMapping("/me")
    public AuthResponse currentUser(Authentication authentication) {
        RoleEntity role = permissionService.getCurrentRole(authentication);
        return new AuthResponse(authentication.getName(), adminAccountService.getDisplayNameByUsername(authentication.getName()), role != null ? role.getDisplayName() : "系统管理员");
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
