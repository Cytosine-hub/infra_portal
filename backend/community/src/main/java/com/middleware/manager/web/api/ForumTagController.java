package com.middleware.manager.web.api;

import com.middleware.manager.service.ForumTagManagementService;
import com.middleware.manager.web.api.dto.ForumTagRequest;
import com.middleware.manager.web.api.dto.ForumTagResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum/my-tags")
public class ForumTagController {
    private final ForumTagManagementService tagService;

    public ForumTagController(ForumTagManagementService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<ForumTagResponse> list(Authentication authentication) {
        return tagService.listPersonal(authentication.getName()).stream().map(ForumTagResponse::from).toList();
    }

    @PutMapping("/{id}")
    public ForumTagResponse rename(@PathVariable Long id, @Valid @RequestBody ForumTagRequest request,
                                   Authentication authentication) {
        return ForumTagResponse.from(tagService.renamePersonal(id, request.getName(), authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        tagService.deletePersonal(id, authentication.getName());
    }
}
