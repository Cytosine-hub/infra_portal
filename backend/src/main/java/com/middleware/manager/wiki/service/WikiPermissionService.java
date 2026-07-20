package com.middleware.manager.wiki.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiPagePermission;
import com.middleware.manager.wiki.repository.WikiPagePermissionMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiPermissionService {

    private final WikiPagePermissionMapper permissionMapper;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public WikiPermissionService(WikiPagePermissionMapper permissionMapper,
                                  PermissionService permissionService,
                                  ObjectMapper objectMapper) {
        this.permissionMapper = permissionMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Check if the authenticated user can view a specific wiki page.
     */
    public boolean canView(Authentication auth, WikiPage page) {
        if (page == null) return false;

        // SYS_ADMIN can view everything
        if (permissionService.isAdmin(auth)) return true;

        // Check page-level permission override
        WikiPagePermission perm = permissionMapper.findByPageId(page.getId());
        if (perm != null) {
            String permType = perm.getPermissionType();
            if ("HIDDEN".equals(permType)) {
                // HIDDEN: only SYS_ADMIN (already handled above) can view
                return false;
            }
            if ("RESTRICTED".equals(permType)) {
                // RESTRICTED: only roles in targetRoles can view
                return isRoleInTargetRoles(auth, perm.getTargetRoles());
            }
            // VISIBLE: fall through to default category-based check
        }

        // Default: use category-based access
        String category = page.getCategory();
        if (category == null || category.isBlank()) {
            // Pages without category are visible to all authenticated users
            return true;
        }
        return permissionService.canManageCategory(auth, category);
    }

    /**
     * Filter a list of wiki pages to only those visible to the user.
     */
    public List<WikiPage> filterVisiblePages(Authentication auth, List<WikiPage> pages) {
        if (pages == null) return new ArrayList<>();
        // SYS_ADMIN sees everything, skip filtering
        if (permissionService.isAdmin(auth)) return pages;
        return pages.stream()
                .filter(page -> canView(auth, page))
                .collect(Collectors.toList());
    }

    /**
     * Create or update page-level permission.
     */
    public WikiPagePermission setPagePermission(Long pageId, String permissionType, String targetRoles, Long userId) {
        WikiPagePermission existing = permissionMapper.findByPageId(pageId);
        if (existing != null) {
            existing.setPermissionType(permissionType);
            existing.setTargetRoles(targetRoles);
            permissionMapper.update(existing);
            return existing;
        } else {
            WikiPagePermission perm = new WikiPagePermission();
            perm.setPageId(pageId);
            perm.setPermissionType(permissionType);
            perm.setTargetRoles(targetRoles);
            perm.setCreatedBy(userId);
            perm.setCreatedAt(LocalDateTime.now());
            permissionMapper.insert(perm);
            return perm;
        }
    }

    /**
     * Get page-level permission override (may return null if none exists).
     */
    public WikiPagePermission getPagePermission(Long pageId) {
        return permissionMapper.findByPageId(pageId);
    }

    /**
     * Check if the user is SYS_ADMIN.
     */
    public boolean isAdmin(Authentication auth) {
        return permissionService.isAdmin(auth);
    }

    /**
     * Check if the user is a category admin for the page's category.
     */
    public boolean isCategoryAdminForPage(Authentication auth, WikiPage page) {
        if (page == null || page.getCategory() == null) return false;
        return permissionService.canManageCategory(auth, page.getCategory());
    }

    /**
     * Get the category the authenticated user can manage.
     */
    public String getManagedCategory(Authentication auth) {
        return permissionService.getManagedCategory(auth);
    }

    /**
     * Check if the user's role is included in the targetRoles JSON array.
     */
    private boolean isRoleInTargetRoles(Authentication auth, String targetRolesJson) {
        if (targetRolesJson == null || targetRolesJson.isBlank()) return false;
        try {
            List<String> targetRoles = objectMapper.readValue(targetRolesJson, new TypeReference<List<String>>() {});
            Set<String> userAuthorities = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            return targetRoles.stream().anyMatch(userAuthorities::contains);
        } catch (Exception e) {
            log.warn("Failed to parse targetRoles JSON: {}", e.getMessage());
            return false;
        }
    }
}
