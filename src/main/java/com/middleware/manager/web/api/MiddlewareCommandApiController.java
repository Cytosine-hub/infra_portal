package com.middleware.manager.web.api;

import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.MiddlewareType;
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

    public MiddlewareCommandApiController(MiddlewareCommandService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public List<MiddlewareType> listTypes() {
        return service.listTypes();
    }

    @GetMapping
    public List<MiddlewareCommand> listCommands(@RequestParam(required = false) Long typeId) {
        return service.listCommands(typeId);
    }

    @PostMapping
    public ResponseEntity<MiddlewareCommand> create(@RequestBody Map<String, Object> body, Authentication auth) {
        requireAuth(auth);
        Long typeId = toLong(body.get("middlewareTypeId"));
        String commandFormat = (String) body.get("commandFormat");
        String briefDesc = (String) body.get("briefDescription");
        String detailDesc = (String) body.get("detailedDescription");
        String categories = (String) body.get("categories");
        int sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        if (typeId == null || commandFormat == null || commandFormat.isBlank()) {
            throw new IllegalArgumentException("类型和命令格式不能为空");
        }
        MiddlewareCommand cmd = service.create(typeId, commandFormat, briefDesc, detailDesc, categories, sortOrder);
        return ResponseEntity.ok(cmd);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MiddlewareCommand> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                    Authentication auth) {
        requireAuth(auth);
        Long typeId = toLong(body.get("middlewareTypeId"));
        String commandFormat = (String) body.get("commandFormat");
        String briefDesc = (String) body.get("briefDescription");
        String detailDesc = (String) body.get("detailedDescription");
        String categories = (String) body.get("categories");
        int sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0;
        MiddlewareCommand cmd = service.update(id, typeId, commandFormat, briefDesc, detailDesc, categories, sortOrder);
        return ResponseEntity.ok(cmd);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        requireAuth(auth);
        service.delete(id);
        return ResponseEntity.noContent().build();
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
