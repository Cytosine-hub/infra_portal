package com.middleware.manager.web.api;

import com.middleware.manager.service.SoftwareTypeService;
import com.middleware.manager.web.api.dto.SoftwareCategoryRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/software-type-categories")
public class AdminSoftwareCategoryApiController {
    private final SoftwareTypeService softwareTypeService;

    public AdminSoftwareCategoryApiController(SoftwareTypeService softwareTypeService) {
        this.softwareTypeService = softwareTypeService;
    }

    @GetMapping
    public List<String> list() {
        return softwareTypeService.listCategories();
    }

    @PostMapping
    public List<String> create(@Valid @RequestBody SoftwareCategoryRequest request) {
        softwareTypeService.createCategory(request.getName());
        return softwareTypeService.listCategories();
    }
}
