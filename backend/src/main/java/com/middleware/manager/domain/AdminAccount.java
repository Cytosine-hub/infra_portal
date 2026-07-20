package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccount {

    private Long id;
    private String username;
    private String displayName;
    private String passwordHash;
    private String role = "系统管理员";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
