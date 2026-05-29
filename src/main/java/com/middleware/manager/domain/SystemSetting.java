package com.middleware.manager.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {
    private String settingKey;
    private String settingValue;
    private String description;
    private LocalDateTime updatedAt;
}
