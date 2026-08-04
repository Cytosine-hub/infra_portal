package com.middleware.manager.web.api;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.CatalogSoftwareTypeProtocol;
import com.middleware.manager.service.SoftwareTypeService;
import com.middleware.manager.web.api.dto.SoftwareTypeCategoryRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeIdsRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeResolveRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CatalogSoftwareTypeProtocol.BASE_PATH)
public class InternalSoftwareTypeApiController {
    private final SoftwareTypeService softwareTypeService;
    private final GatewaySignatureService signatureService;

    public InternalSoftwareTypeApiController(SoftwareTypeService softwareTypeService,
                                             GatewaySignatureService signatureService) {
        this.softwareTypeService = softwareTypeService;
        this.signatureService = signatureService;
    }

    @PostMapping(CatalogSoftwareTypeProtocol.ACTIVE_PATH)
    public List<SoftwareTypeResponse> findActive(
            @RequestHeader(value = GatewayIdentityHeaders.SIGNATURE, required = false) String signature) {
        verify(CatalogSoftwareTypeProtocol.ACTIVE_OPERATION, "", signature);
        return softwareTypeService.findActive().stream()
                .map(SoftwareTypeResponse::from)
                .toList();
    }

    @PostMapping(CatalogSoftwareTypeProtocol.BY_IDS_PATH)
    public List<SoftwareTypeResponse> findByIds(
            @Valid @RequestBody SoftwareTypeIdsRequest request,
            @RequestHeader(value = GatewayIdentityHeaders.SIGNATURE, required = false) String signature) {
        verify(CatalogSoftwareTypeProtocol.BY_IDS_OPERATION,
                CatalogSoftwareTypeProtocol.idsPayload(request.getIds()), signature);
        return softwareTypeService.findByIds(request.getIds()).stream()
                .map(SoftwareTypeResponse::from)
                .toList();
    }

    @PostMapping(CatalogSoftwareTypeProtocol.BY_CATEGORY_PATH)
    public List<SoftwareTypeResponse> findByCategory(
            @Valid @RequestBody SoftwareTypeCategoryRequest request,
            @RequestHeader(value = GatewayIdentityHeaders.SIGNATURE, required = false) String signature) {
        verify(CatalogSoftwareTypeProtocol.BY_CATEGORY_OPERATION,
                CatalogSoftwareTypeProtocol.categoryPayload(request.getCategoryName()), signature);
        return softwareTypeService.findByCategory(request.getCategoryName()).stream()
                .map(SoftwareTypeResponse::from)
                .toList();
    }

    @PostMapping(CatalogSoftwareTypeProtocol.RESOLVE_PATH)
    public SoftwareTypeResponse resolveOrCreate(
            @Valid @RequestBody SoftwareTypeResolveRequest request,
            @RequestHeader(value = GatewayIdentityHeaders.SIGNATURE, required = false) String signature) {
        verify(CatalogSoftwareTypeProtocol.RESOLVE_OPERATION,
                CatalogSoftwareTypeProtocol.resolvePayload(
                        request.getCategoryName(), request.getSoftwareTypeName()),
                signature);
        return SoftwareTypeResponse.from(softwareTypeService.resolveOrCreate(
                request.getCategoryName(), request.getSoftwareTypeName()));
    }

    private void verify(String operation, String payload, String signature) {
        if (!signatureService.verifyInternalRequest(operation, payload, signature)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
    }
}
