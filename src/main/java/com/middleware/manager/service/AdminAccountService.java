package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.AdminAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Order(1)
@Slf4j
public class AdminAccountService implements UserDetailsService, ApplicationRunner {
    private static final String ROLE_DEV_MGR = "ROLE_DEV_MGR";
    private static final String SYSTEM_ADMIN = "系统管理员";
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AdminAccountMapper mapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public AdminAccountService(AdminAccountMapper mapper,
                               RoleService roleService,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.security.admin.default-password:admin123}") String defaultPassword) {
        this.mapper = mapper;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedDefaultAccounts();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminAccount account = mapper.findByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException(ErrorMessages.USER_NOT_FOUND);
        }

        RoleEntity role = roleService.getByDisplayName(account.getRole());
        String authority = role != null ? role.getAuthority() : ROLE_DEV_MGR;
        return new User(account.getUsername(), account.getPasswordHash(),
                AuthorityUtils.createAuthorityList(authority));
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminAccount account = mapper.findByUsername(username);
        if (account == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID, ErrorMessages.PASSWORD_INVALID);
        }
        account.setPasswordHash(encodePassword(sha256Hex(newPassword)));
        account.setUpdatedAt(LocalDateTime.now());
        mapper.update(account);
        log.info("密码已修改 username={}", username);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        AdminAccount account = mapper.findById(userId);
        if (account == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND);
        }
        if (!StringUtils.hasText(newPassword) || newPassword.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT, ErrorMessages.PASSWORD_TOO_SHORT);
        }
        account.setPasswordHash(encodePassword(sha256Hex(newPassword.trim())));
        account.setUpdatedAt(LocalDateTime.now());
        mapper.update(account);
        log.info("密码已重置 userId={}", userId);
    }

    public String getDisplayNameByUsername(String username) {
        AdminAccount account = mapper.findByUsername(username);
        return account != null ? account.getDisplayName() : username;
    }

    public List<AdminAccount> listUsers() {
        return mapper.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public AdminAccount createUser(String username, String displayName, String password, String role) {
        if (mapper.findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USER_DUPLICATE, ErrorMessages.USER_DUPLICATE);
        }
        if (roleService.getByDisplayName(role) == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, ErrorMessages.ROLE_NOT_FOUND, role);
        }
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setDisplayName(displayName != null ? displayName : username);
        account.setPasswordHash(encodePassword(password));
        account.setRole(role);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        mapper.insert(account);
        log.info("用户已创建 username={}", username);
        return account;
    }

    private String encodePassword(String raw) {
        return passwordEncoder.encode(raw);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.SHA256_UNAVAILABLE);
        }
    }

    @Transactional
    public AdminAccount updateUserRole(Long userId, String newRole) {
        if (roleService.getByDisplayName(newRole) == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND, ErrorMessages.ROLE_NOT_FOUND, newRole);
        }
        AdminAccount account = mapper.findById(userId);
        if (account == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND);
        }
        account.setRole(newRole);
        account.setUpdatedAt(LocalDateTime.now());
        mapper.update(account);
        log.info("用户角色已更新 userId={}, role={}", userId, newRole);
        return account;
    }

    @Transactional
    public void deleteUser(Long userId) {
        AdminAccount account = mapper.findById(userId);
        if (account == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, ErrorMessages.USER_NOT_FOUND);
        }
        if (SYSTEM_ADMIN.equals(account.getRole()) && mapper.countByRole(SYSTEM_ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_CANNOT_DELETE, ErrorMessages.LAST_ADMIN_CANNOT_DELETE);
        }
        mapper.deleteById(userId);
        log.info("用户已删除 userId={}", userId);
    }

    private void seedDefaultAccounts() {
        if (mapper.count() > 0) return;

        String[][] defaultAccounts = {
            {"系统管理员", "sysadmin", "系统管理员"},
            {"中间件管理员", "mwadmin", "中间件管理员"},
            {"数据库管理员", "dbadmin", "数据库管理员"},
            {"主机管理员", "hostadmin", "主机管理员"},
            {"网络管理员", "netadmin", "网络管理员"},
            {"网络安全管理员", "secadmin", "安全管理员"},
            {"中间件管理岗", "mwmgr", "中间件管理岗"},
            {"数据库管理岗", "dbmgr", "数据库管理岗"},
            {"主机管理岗", "hostmgr", "主机管理岗"},
            {"网络管理岗", "netmgr", "网络管理岗"},
            {"网络安全岗", "secmgr", "网络安全管理岗"},
            {"开发经理", "devmgr", "开发经理"},
            {"运维经理", "opsmgr", "运维经理"},
        };

        for (String[] entry : defaultAccounts) {
            AdminAccount account = new AdminAccount();
            account.setRole(entry[0]);
            account.setUsername(entry[1]);
            account.setDisplayName(entry[2]);
            account.setPasswordHash(encodePassword(sha256Hex(defaultPassword)));
            account.setCreatedAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            mapper.insert(account);
        }
        log.info("已创建默认管理员账号 count={}", defaultAccounts.length);
    }
}
