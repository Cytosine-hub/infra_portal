package com.middleware.manager.web.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String username;
    private String displayName;
    private String role;
    private String token;
    private LocalDateTime expiresAt;

    public AuthResponse(String username, String displayName, String role) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }
}
