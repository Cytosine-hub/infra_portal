package com.middleware.manager.web.api;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ParameterStandardService;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.web.api.dto.PageResult;
import com.middleware.manager.web.api.dto.ParameterStandardRequest;
import com.middleware.manager.web.api.dto.ParameterStandardResponse;
import com.middleware.manager.web.api.dto.StandardDocumentResponse;
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
@RequestMapping("/api/admin/parameter-standards")
public class AdminParameterStandardController {
    private final ParameterStandardService service;
    private final StandardDocumentService documentService;
    private final PermissionService permissionService;

    public AdminParameterStandardController(ParameterStandardService service,
                                            StandardDocumentService documentService,
                                            PermissionService permissionService) {
        this.service = service;
        this.documentService = documentService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public PageResult<ParameterStandardResponse> list(@RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(defaultValue = "") String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                Authentication authentication) {
        String category = permissionService.getManagedCategory(authentication);
        var pageInfo = service.list(keyword, status, category, page, size);
        PageResult<ParameterStandardResponse> result = new PageResult<>();
        result.setContent(pageInfo.getList().stream()
                .map(doc -> {
                    ParameterStandardResponse resp = ParameterStandardResponse.from(doc, service.render(doc));
                    List<StandardDocumentResponse> relatedDocs = documentService.listPublishedRelatedDocuments(doc.getId())
                            .stream()
                            .map(rd -> StandardDocumentResponse.from(rd, null))
                            .collect(Collectors.toList());
                    resp.setRelatedDocuments(relatedDocs);
                    return resp;
                })
                .collect(Collectors.toList()));
        result.setPage(pageInfo.getPageNum() - 1);
        result.setSize(pageInfo.getPageSize());
        result.setTotalElements(pageInfo.getTotal());
        result.setTotalPages(pageInfo.getPages());
        result.setFirst(pageInfo.isIsFirstPage());
        result.setLast(pageInfo.isIsLastPage());
        return result;
    }

    @GetMapping("/{id}")
    public ParameterStandardResponse detail(@PathVariable Long id, Authentication authentication) {
        ParameterStandard standard = checkAccess(id, authentication);
        ParameterStandardResponse resp = ParameterStandardResponse.from(standard, service.render(standard));
        List<StandardDocumentResponse> relatedDocs = documentService.listPublishedRelatedDocuments(standard.getId())
                .stream()
                .map(rd -> StandardDocumentResponse.from(rd, null))
                .collect(Collectors.toList());
        resp.setRelatedDocuments(relatedDocs);
        return resp;
    }

    @PostMapping
    public ParameterStandardResponse create(@Valid @RequestBody ParameterStandardRequest request) {
        ParameterStandard standard = service.create(request);
        return ParameterStandardResponse.from(standard, service.render(standard));
    }

    @PutMapping("/{id}")
    public ParameterStandardResponse update(@PathVariable Long id,
                                            @Valid @RequestBody ParameterStandardRequest request,
                                            Authentication authentication) {
        checkAccess(id, authentication);
        ParameterStandard standard = service.update(id, request);
        return ParameterStandardResponse.from(standard, service.render(standard));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        service.delete(id);
    }

    @PostMapping("/{id}/submit-review")
    public ParameterStandardResponse submitReview(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        ParameterStandard standard = service.submitForReview(id, authentication.getName(), authentication.getName());
        return ParameterStandardResponse.from(standard, service.render(standard));
    }

    @PostMapping("/{id}/publish")
    public ParameterStandardResponse publish(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        ParameterStandard standard = service.publish(id);
        return ParameterStandardResponse.from(standard, service.render(standard));
    }

    @PostMapping("/{id}/start-modify")
    public ParameterStandardResponse startModify(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        ParameterStandard standard = service.startModify(id);
        return ParameterStandardResponse.from(standard, service.render(standard));
    }

    @PostMapping("/{id}/cancel-modify")
    public ParameterStandardResponse cancelModify(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        ParameterStandard standard = service.cancelModify(id);
        return ParameterStandardResponse.from(standard, service.render(standard));
    }

    private ParameterStandard checkAccess(Long id, Authentication authentication) {
        ParameterStandard standard = service.get(id);
        if (!permissionService.canManageCategory(authentication, standard.getCategory())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该分类参数标准");
        }
        return standard;
    }
}
