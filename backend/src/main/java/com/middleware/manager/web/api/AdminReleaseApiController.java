package com.middleware.manager.web.api;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.service.StandardPackageService;
import com.middleware.manager.web.api.dto.PageResult;
import com.middleware.manager.web.api.dto.ReleaseResponse;
import com.middleware.manager.web.form.BatchImportForm;
import com.middleware.manager.web.form.ReleaseForm;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/releases")
public class AdminReleaseApiController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReleaseService releaseService;
    private final PermissionService permissionService;
    private final SoftwareTypeMapper softwareTypeMapper;
    private final StandardPackageService standardPackageService;

    public AdminReleaseApiController(ReleaseService releaseService, PermissionService permissionService,
                                     SoftwareTypeMapper softwareTypeMapper,
                                     StandardPackageService standardPackageService) {
        this.releaseService = releaseService;
        this.permissionService = permissionService;
        this.softwareTypeMapper = softwareTypeMapper;
        this.standardPackageService = standardPackageService;
    }

    @GetMapping
    public PageResult<ReleaseResponse> list(@RequestParam(defaultValue = "") String keyword,
                                              @RequestParam(defaultValue = "") String platform,
                                              @RequestParam(required = false) Boolean published,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              Authentication authentication) {
        String category = permissionService.getManagedCategory(authentication);
        var pageInfo = releaseService.listAdminReleases(keyword, platform, published, category, page, normalizeSize(size));
        PageResult<ReleaseResponse> result = new PageResult<>();
        result.setContent(pageInfo.getList().stream()
                .map(a -> ReleaseResponse.from(a, loadSoftwareType(a)))
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
    public ReleaseResponse detail(@PathVariable Long id, Authentication authentication) {
        ReleaseAsset asset = checkAccess(id, authentication);
        return ReleaseResponse.from(asset, loadSoftwareType(asset));
    }

    @PostMapping
    public ReleaseResponse create(@Valid @ModelAttribute ReleaseForm form, BindingResult bindingResult,
                                   Authentication authentication) throws BindException {
        if (form.getFile() == null || form.getFile().isEmpty()) {
            bindingResult.rejectValue("file", "file.required", "请上传安装包");
        }
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        String managedCategory = permissionService.getManagedCategory(authentication);
        if (managedCategory != null && form.getSoftwareTypeId() != null) {
            SoftwareType type = softwareTypeMapper.findById(form.getSoftwareTypeId());
            if (type != null && !managedCategory.equals(type.getCategory())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本岗位分类的资源");
            }
        }
        ReleaseAsset created = releaseService.create(form);
        return ReleaseResponse.from(created, loadSoftwareType(created));
    }

    @PutMapping("/{id}")
    public ReleaseResponse update(@PathVariable Long id,
                                  @Valid @ModelAttribute ReleaseForm form,
                                  BindingResult bindingResult,
                                  Authentication authentication) throws BindException {
        checkAccess(id, authentication);
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        ReleaseAsset updated = releaseService.update(id, form);
        return ReleaseResponse.from(updated, loadSoftwareType(updated));
    }

    @PostMapping("/import")
    public ReleaseService.BatchImportResult batchImport(@Valid @RequestBody BatchImportForm form,
                                                        Authentication authentication) {
        String managedCategory = permissionService.getManagedCategory(authentication);
        if (managedCategory != null && form.getSoftwareTypeId() != null) {
            SoftwareType type = softwareTypeMapper.findById(form.getSoftwareTypeId());
            if (type != null && !managedCategory.equals(type.getCategory())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能操作本岗位分类的资源");
            }
        }
        return releaseService.batchImport(form);
    }

    @PostMapping("/{id}/publish")
    public ReleaseResponse publish(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        releaseService.publish(id);
        ReleaseAsset asset = releaseService.getAdminRelease(id);
        return ReleaseResponse.from(asset, loadSoftwareType(asset));
    }

    @PostMapping("/{id}/unpublish")
    public ReleaseResponse unpublish(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        releaseService.unpublish(id);
        ReleaseAsset asset = releaseService.getAdminRelease(id);
        return ReleaseResponse.from(asset, loadSoftwareType(asset));
    }

    @PostMapping("/{id}/regenerate-package")
    public ReleaseResponse regeneratePackage(@PathVariable Long id, Authentication authentication) {
        ReleaseAsset asset = checkAccess(id, authentication);
        if (!asset.isStandardPackage()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该资源不是标准包");
        }
        standardPackageService.processAsync(asset);
        ReleaseAsset refreshed = releaseService.getAdminRelease(id);
        return ReleaseResponse.from(refreshed, loadSoftwareType(refreshed));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        checkAccess(id, authentication);
        releaseService.delete(id);
    }

    private ReleaseAsset getReleaseOr404(Long id) {
        try {
            return releaseService.getAdminRelease(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private ReleaseAsset checkAccess(Long id, Authentication authentication) {
        ReleaseAsset release = getReleaseOr404(id);
        if (!permissionService.canManageCategory(authentication, releaseCategory(release))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该分类资源");
        }
        return release;
    }

    private String releaseCategory(ReleaseAsset release) {
        if (release.getSoftwareTypeId() == null) return null;
        SoftwareType type = softwareTypeMapper.findById(release.getSoftwareTypeId());
        return type != null ? type.getCategory() : null;
    }

    private SoftwareType loadSoftwareType(ReleaseAsset asset) {
        if (asset.getSoftwareTypeId() == null) return null;
        return softwareTypeMapper.findById(asset.getSoftwareTypeId());
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
