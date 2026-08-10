package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ForumTag;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForumTagResponse {
    private Long id;
    private String name;
    private int postCount;
    private String category;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ForumTagResponse from(ForumTag tag) {
        return new ForumTagResponse(
                tag.getId(), tag.getName(), tag.getPostCount(), tag.getCategory(),
                tag.getCreatedBy(), tag.getCreatedAt(), tag.getUpdatedAt());
    }
}
