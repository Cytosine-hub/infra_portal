package com.middleware.manager.web.api;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ForumTagManagementService;
import com.middleware.manager.web.api.dto.ForumTagRequest;
import com.middleware.manager.web.api.dto.ForumTagResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/admin/tags")
public class AdminForumTagController {
    private final ForumTagManagementService tagService;
    private final PermissionService permissionService;

    public AdminForumTagController(ForumTagManagementService tagService, PermissionService permissionService) {
        this.tagService = tagService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<ForumTagResponse> list(Authentication authentication) {
        requireForumAdmin(authentication);
        boolean systemAdmin = permissionService.isAdmin(authentication);
        String category = systemAdmin ? null : permissionService.requireManagedCategory(authentication);
        return tagService.listAdmin(category, systemAdmin).stream().map(ForumTagResponse::from).toList();
    }

    @PostMapping
    public ForumTagResponse create(@Valid @RequestBody ForumTagRequest request, Authentication authentication) {
        requireForumAdmin(authentication);
        boolean systemAdmin = permissionService.isAdmin(authentication);
        String category = systemAdmin ? request.getCategory() : permissionService.requireManagedCategory(authentication);
        return ForumTagResponse.from(tagService.createAdmin(request.getName(), category, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ForumTagResponse rename(@PathVariable Long id, @Valid @RequestBody ForumTagRequest request,
                                   Authentication authentication) {
        requireForumAdmin(authentication);
        boolean systemAdmin = permissionService.isAdmin(authentication);
        String category = systemAdmin ? null : permissionService.requireManagedCategory(authentication);
        return ForumTagResponse.from(tagService.renameAdmin(id, request.getName(), category, systemAdmin));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        requireForumAdmin(authentication);
        boolean systemAdmin = permissionService.isAdmin(authentication);
        String category = systemAdmin ? null : permissionService.requireManagedCategory(authentication);
        tagService.deleteAdmin(id, category, systemAdmin);
    }

    private void requireForumAdmin(Authentication authentication) {
        if (!permissionService.canReviewAny(authentication)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
    }
}
