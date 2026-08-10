package com.middleware.manager.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForumTag {
    private Long id;
    private String name;
    private int postCount;
    private String category;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 仅在批量查询 findByPostIds 时填充，用于按帖子分组 */
    private Long postId;
}
