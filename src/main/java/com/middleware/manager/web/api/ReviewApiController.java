package com.middleware.manager.web.api;

import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ReviewService;
import com.middleware.manager.web.api.dto.ReviewRequest;
import com.middleware.manager.web.api.dto.ReviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reviews")
public class ReviewApiController {
    private final ReviewService reviewService;
    private final PermissionService permissionService;

    public ReviewApiController(ReviewService reviewService, PermissionService permissionService) {
        this.reviewService = reviewService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<ReviewResponse> list(Authentication authentication) {
        return reviewService.listReviews(authentication);
    }

    @GetMapping("/{id}")
    public ReviewResponse detail(@PathVariable Long id) {
        return reviewService.getReviewDetail(id);
    }

    @PostMapping("/{id}/approve")
    public ReviewResponse approve(@PathVariable Long id,
                                  @RequestBody(required = false) ReviewRequest request,
                                  Authentication authentication) {
        ReviewResponse detail = reviewService.getReviewDetail(id);
        if (!permissionService.canReview(authentication, detail.getCategory())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权审核此分类文档");
        }
        String reviewer = authentication.getName();
        String comment = request != null ? request.getComment() : null;
        return reviewService.approve(id, reviewer, comment);
    }

    @PostMapping("/{id}/reject")
    public ReviewResponse reject(@PathVariable Long id,
                                 @RequestBody(required = false) ReviewRequest request,
                                 Authentication authentication) {
        ReviewResponse detail = reviewService.getReviewDetail(id);
        if (!permissionService.canReview(authentication, detail.getCategory())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权审核此分类文档");
        }
        String reviewer = authentication.getName();
        String comment = request != null ? request.getComment() : null;
        return reviewService.reject(id, reviewer, comment);
    }
}
