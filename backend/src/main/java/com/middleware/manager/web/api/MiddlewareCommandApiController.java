package com.middleware.manager.web.api;

import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.MiddlewareCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/middleware-commands")
public class MiddlewareCommandApiController {

    private final MiddlewareCommandService service;
    private final PermissionService permissionService;
    private final SoftwareTypeMapper softwareTypeMapper;

    public MiddlewareCommandApiController(MiddlewareCommandService service,
                                          PermissionService permissionService,
                                          SoftwareTypeMapper softwareTypeMapper) {
        this.service = service;
        this.permissionService = permissionService;
        this.softwareTypeMapper = softwareTypeMapper;
    }

    @GetMapping("/types")
    public List<SoftwareType> listTypes() {
        return service.listTypes();
    }

    @GetMapping
    public List<MiddlewareCommand> listCommands(@RequestParam(required = false) Long typeId,
                                                 @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return service.listCommandsByCategory(category);
        }
        return service.listCommands(typeId);
    }

    @PostMapping
    public ResponseEntity<MiddlewareCommand> create(@RequestBody Map<String, Object> body, Authentication auth) {
        requireAuth(auth);
        Long softwareTypeId = toLong(body.get("softwareTypeId"));
        String commandFormat = (String) body.get("commandFormat");
        String briefDesc = (String) body.get("briefDescription");
        String detailDesc = (String) body.get("detailedDescription");
        String categories = (String) body.get("categories");
        int sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        if (softwareTypeId == null || commandFormat == null || commandFormat.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "类型和命令格式不能为空");
        }
        SoftwareType type = softwareTypeMapper.findById(softwareTypeId);
        if (type == null) {
            throw new NotFoundException(ErrorCode.SOFTWARE_TYPE_NOT_FOUND, ErrorMessages.SOFTWARE_TYPE_NOT_FOUND);
        }
        checkCategoryAccess(auth, type.getCategory());
        MiddlewareCommand cmd = service.create(softwareTypeId, commandFormat, briefDesc, detailDesc, categories, sortOrder);
        return ResponseEntity.ok(cmd);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MiddlewareCommand> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                    Authentication auth) {
        requireAuth(auth);
        MiddlewareCommand existing = service.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "命令不存在");
        }
        SoftwareType existingType = softwareTypeMapper.findById(existing.getSoftwareTypeId());
        if (existingType != null) {
            checkCategoryAccess(auth, existingType.getCategory());
        }

        Long softwareTypeId = toLong(body.get("softwareTypeId"));
        String commandFormat = (String) body.get("commandFormat");
        String briefDesc = (String) body.get("briefDescription");
        String detailDesc = (String) body.get("detailedDescription");
        String categories = (String) body.get("categories");
        int sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        MiddlewareCommand cmd = service.update(id, softwareTypeId, commandFormat, briefDesc, detailDesc, categories, sortOrder);
        return ResponseEntity.ok(cmd);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        requireAuth(auth);
        MiddlewareCommand existing = service.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "命令不存在");
        }
        SoftwareType type = softwareTypeMapper.findById(existing.getSoftwareTypeId());
        if (type != null) {
            checkCategoryAccess(auth, type.getCategory());
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void checkCategoryAccess(Authentication auth, String category) {
        if (permissionService.isAdmin(auth)) return;
        if (!permissionService.canManageCategory(auth, category)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本岗位分类的资源");
        }
    }

    private void requireAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要登录");
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
