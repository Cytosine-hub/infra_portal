package com.middleware.community.security;

import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.repository.RoleMapper;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CommunityUserDetailsService implements UserDetailsService {
    private static final String DEFAULT_AUTHORITY = "ROLE_DEV_MGR";

    private final AdminAccountMapper accountMapper;
    private final RoleMapper roleMapper;

    public CommunityUserDetailsService(AdminAccountMapper accountMapper, RoleMapper roleMapper) {
        this.accountMapper = accountMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminAccount account = accountMapper.findByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException(ErrorMessages.USER_NOT_FOUND);
        }

        RoleEntity role = roleMapper.findByDisplayName(account.getRole());
        String authority = role != null ? role.getAuthority() : DEFAULT_AUTHORITY;
        return new User(account.getUsername(), account.getPasswordHash(),
                AuthorityUtils.createAuthorityList(authority));
    }
}
