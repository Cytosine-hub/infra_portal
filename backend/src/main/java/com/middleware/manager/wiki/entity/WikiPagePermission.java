package com.middleware.manager.wiki.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiPagePermission {
    private Long id;
    private Long pageId;
    private String permissionType;  // VISIBLE, RESTRICTED, HIDDEN
    private String targetRoles;     // JSON array of role names
    private Long createdBy;
    private LocalDateTime createdAt;
}
