package com.middleware.manager.web.api;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.ForumTag;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ForumTagManagementService;
import com.middleware.manager.web.api.dto.ForumTagRequest;
import com.middleware.manager.web.api.dto.ForumTagResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/forum-tags")
public class AdminForumTagApiController {
    private static final String DEFAULT_TAG_CATEGORY = "未分组";

    private final ForumTagManagementService tagService;
    private final PermissionService permissionService;

    public AdminForumTagApiController(ForumTagManagementService tagService,
                                      PermissionService permissionService) {
        this.tagService = tagService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<ForumTagResponse> list(Authentication authentication) {
        requireForumAdmin(authentication);
        List<ForumTag> tags = permissionService.isAdmin(authentication)
                ? tagService.listAll()
                : tagService.listByCategory(permissionService.requireManagedCategory(authentication));
        return tags.stream().map(ForumTagResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ForumTagResponse create(@Valid @RequestBody ForumTagRequest request,
                                   Authentication authentication) {
        requireForumAdmin(authentication);
        String category = resolveCreateCategory(authentication);
        return ForumTagResponse.from(tagService.create(request.getName(), category, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ForumTagResponse rename(@PathVariable Long id,
                                   @Valid @RequestBody ForumTagRequest request,
                                   Authentication authentication) {
        requireForumAdmin(authentication);
        requireTagAccess(tagService.get(id), authentication);
        return ForumTagResponse.from(tagService.rename(id, request.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        requireForumAdmin(authentication);
        requireTagAccess(tagService.get(id), authentication);
        tagService.delete(id);
    }

    private String resolveCreateCategory(Authentication authentication) {
        if (permissionService.isAdmin(authentication)) {
            return DEFAULT_TAG_CATEGORY;
        }
        return permissionService.requireManagedCategory(authentication);
    }

    private void requireTagAccess(ForumTag tag, Authentication authentication) {
        if (!permissionService.canManageCategory(authentication, tag.getCategory())) {
            throw forbidden();
        }
    }

    private void requireForumAdmin(Authentication authentication) {
        if (!permissionService.isAdmin(authentication)
                && !permissionService.isCategoryAdmin(authentication)) {
            throw forbidden();
        }
    }

    private ForbiddenException forbidden() {
        return new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
    }
}
