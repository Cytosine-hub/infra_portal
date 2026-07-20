package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {
    private Long id;
    private String displayName;
    private String authority;
    private String managedCategory;
    private boolean categoryAdmin;
    private boolean systemRole;
    private LocalDateTime createdAt;
}
