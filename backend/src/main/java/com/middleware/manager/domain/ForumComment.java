package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForumComment {
    private Long id;
    private Long postId;
    private String content;
    private String authorUsername;
    private String authorDisplayName;
    private Long parentId;
    private int likeCount;
    private LocalDateTime createdAt;
}
