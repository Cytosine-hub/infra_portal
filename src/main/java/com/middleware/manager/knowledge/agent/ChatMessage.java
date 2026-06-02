package com.middleware.manager.knowledge.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String referencesText;
    private LocalDateTime createdAt;
}
