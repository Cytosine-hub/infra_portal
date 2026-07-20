package com.middleware.manager.web.api;

import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.SoftwareTypeService;
import com.middleware.manager.web.api.dto.SoftwareTypeRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/software-types")
public class AdminSoftwareTypeApiController {
    private final SoftwareTypeService softwareTypeService;
    private final PermissionService permissionService;

    public AdminSoftwareTypeApiController(SoftwareTypeService softwareTypeService, PermissionService permissionService) {
        this.softwareTypeService = softwareTypeService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<SoftwareTypeResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly,
                                            Authentication authentication) {
        List<SoftwareType> types = softwareTypeService.list(activeOnly);
        String category = permissionService.getManagedCategory(authentication);
        if (category != null) {
            types = types.stream().filter(t -> category.equals(t.getCategory())).collect(Collectors.toList());
        }
        return types.stream().map(SoftwareTypeResponse::from).collect(Collectors.toList());
    }

    @PostMapping
    public SoftwareTypeResponse create(@Valid @RequestBody SoftwareTypeRequest request,
                                        Authentication authentication) {
        checkSysAdmin(authentication);
        return SoftwareTypeResponse.from(softwareTypeService.create(request));
    }

    @PutMapping("/{id}")
    public SoftwareTypeResponse update(@PathVariable Long id,
                                       @Valid @RequestBody SoftwareTypeRequest request,
                                       Authentication authentication) {
        checkSysAdmin(authentication);
        return SoftwareTypeResponse.from(softwareTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        checkSysAdmin(authentication);
        softwareTypeService.delete(id);
    }

    private void checkSysAdmin(Authentication authentication) {
        if (!permissionService.isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统管理员可操作类型管理");
        }
    }
}
