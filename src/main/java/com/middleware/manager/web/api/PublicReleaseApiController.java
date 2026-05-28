package com.middleware.manager.web.api;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.SoftwareTypeMapper;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.web.api.dto.PageResult;
import com.middleware.manager.web.api.dto.ReleaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/releases")
public class PublicReleaseApiController {
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;

    private final ReleaseService releaseService;
    private final SoftwareTypeMapper softwareTypeMapper;

    public PublicReleaseApiController(ReleaseService releaseService, SoftwareTypeMapper softwareTypeMapper) {
        this.releaseService = releaseService;
        this.softwareTypeMapper = softwareTypeMapper;
    }

    @GetMapping
    public PageResult<ReleaseResponse> list(@RequestParam(defaultValue = "") String keyword,
                                              @RequestParam(defaultValue = "") String platform,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        var pageInfo = releaseService.listPublishedReleases(keyword, platform, page, normalizeSize(size));
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

    @GetMapping("/{token}")
    public ReleaseResponse detail(@PathVariable String token) {
        try {
            ReleaseAsset asset = releaseService.getPublishedRelease(token);
            return ReleaseResponse.from(asset, loadSoftwareType(asset));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
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
