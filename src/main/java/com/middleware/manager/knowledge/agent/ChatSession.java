package com.middleware.manager.knowledge.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private Long id;
    private String title;
    private String mode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
