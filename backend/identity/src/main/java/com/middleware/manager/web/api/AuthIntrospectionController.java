package com.middleware.manager.web.api;

import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.GatewayAuthenticationToken;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import com.middleware.manager.service.TokenService;
import com.middleware.manager.web.api.dto.AuthIntrospectionRequest;
import com.middleware.manager.web.api.dto.AuthIntrospectionResponse;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthIntrospectionController {
    private final TokenService tokenService;
    private final AdminAccountService adminAccountService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final GatewaySignatureService signatureService;

    public AuthIntrospectionController(TokenService tokenService,
                                       AdminAccountService adminAccountService,
                                       RoleService roleService,
                                       PermissionService permissionService,
                                       GatewaySignatureService signatureService) {
        this.tokenService = tokenService;
        this.adminAccountService = adminAccountService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.signatureService = signatureService;
    }

    @PostMapping("/introspect")
    public AuthIntrospectionResponse introspect(
            @RequestBody AuthIntrospectionRequest request,
            @RequestHeader(value = GatewayIdentityHeaders.SIGNATURE, required = false) String signature) {
        String token = request == null ? null : request.token();
        if (!signatureService.verifyIntrospectionToken(token, signature)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }

        String username = tokenService.validateToken(token);
        if (username == null) {
            return AuthIntrospectionResponse.invalid();
        }

        UserDetails userDetails = adminAccountService.loadUserByUsername(username);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList();
        RoleEntity primaryRole = roles.stream()
                .map(roleService::getByAuthority)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        String roleCategory = primaryRole == null ? null : primaryRole.getManagedCategory();
        boolean roleCategoryAdmin = primaryRole != null && primaryRole.isCategoryAdmin();
        GatewayAuthenticationToken authentication = GatewayAuthenticationToken.authenticated(
                username,
                adminAccountService.getDisplayNameByUsername(username),
                roles,
                roleCategory,
                roleCategoryAdmin);

        return new AuthIntrospectionResponse(
                true,
                username,
                authentication.getDisplayName(),
                roles,
                permissionService.getManagedCategory(authentication),
                permissionService.isCategoryAdmin(authentication));
    }
}
