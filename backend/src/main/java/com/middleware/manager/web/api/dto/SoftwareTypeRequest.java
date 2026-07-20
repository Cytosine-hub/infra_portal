package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SoftwareTypeRequest {
    @NotBlank
    @Size(max = 40)
    private String category;

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 500)
    private String description;

    private boolean active = true;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
