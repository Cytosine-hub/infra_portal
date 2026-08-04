package com.middleware.manager.integration;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.CatalogSoftwareTypeProtocol;
import com.middleware.manager.service.SoftwareTypeLookup;
import com.middleware.manager.web.api.dto.SoftwareTypeCategoryRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeIdsRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeResolveRequest;
import com.middleware.manager.web.api.dto.SoftwareTypeResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
public class RemoteSoftwareTypeLookup implements SoftwareTypeLookup {
    private final RestClient restClient;
    private final GatewaySignatureService signatureService;

    public RemoteSoftwareTypeLookup(RestClient restClient,
                                    GatewaySignatureService signatureService) {
        this.restClient = restClient;
        this.signatureService = signatureService;
    }

    @Override
    public List<SoftwareType> findActive() {
        SoftwareTypeResponse[] response = post(
                CatalogSoftwareTypeProtocol.ACTIVE_PATH,
                CatalogSoftwareTypeProtocol.ACTIVE_OPERATION,
                "",
                Map.of(),
                SoftwareTypeResponse[].class);
        return response == null ? List.of() : Arrays.stream(response).map(this::toDomain).toList();
    }

    @Override
    public SoftwareType get(Long id) {
        List<SoftwareType> types = findByIds(List.of(id));
        if (types.isEmpty()) {
            throw new NotFoundException(
                    ErrorCode.SOFTWARE_TYPE_NOT_FOUND, ErrorMessages.SOFTWARE_TYPE_NOT_FOUND);
        }
        return types.get(0);
    }

    @Override
    public List<SoftwareType> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        SoftwareTypeIdsRequest request = new SoftwareTypeIdsRequest(ids);
        SoftwareTypeResponse[] response = post(
                CatalogSoftwareTypeProtocol.BY_IDS_PATH,
                CatalogSoftwareTypeProtocol.BY_IDS_OPERATION,
                CatalogSoftwareTypeProtocol.idsPayload(ids),
                request,
                SoftwareTypeResponse[].class);
        return response == null ? List.of() : Arrays.stream(response).map(this::toDomain).toList();
    }

    @Override
    public List<SoftwareType> findByCategory(String category) {
        SoftwareTypeCategoryRequest request = new SoftwareTypeCategoryRequest(category);
        SoftwareTypeResponse[] response = post(
                CatalogSoftwareTypeProtocol.BY_CATEGORY_PATH,
                CatalogSoftwareTypeProtocol.BY_CATEGORY_OPERATION,
                CatalogSoftwareTypeProtocol.categoryPayload(category),
                request,
                SoftwareTypeResponse[].class);
        return response == null ? List.of() : Arrays.stream(response).map(this::toDomain).toList();
    }

    @Override
    public SoftwareType resolveOrCreate(String category, String name) {
        SoftwareTypeResolveRequest request = new SoftwareTypeResolveRequest(category, name);
        SoftwareTypeResponse response = post(
                CatalogSoftwareTypeProtocol.RESOLVE_PATH,
                CatalogSoftwareTypeProtocol.RESOLVE_OPERATION,
                CatalogSoftwareTypeProtocol.resolvePayload(category, name),
                request,
                SoftwareTypeResponse.class);
        if (response == null) {
            throw lookupFailure(null);
        }
        return toDomain(response);
    }

    private <T> T post(String path, String operation, String payload,
                       Object request, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(CatalogSoftwareTypeProtocol.BASE_PATH + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(GatewayIdentityHeaders.SIGNATURE,
                            signatureService.signInternalRequest(operation, payload))
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw lookupFailure(exception);
        }
    }

    private BusinessException lookupFailure(Exception exception) {
        if (exception == null) {
            log.warn("catalog 软件类型解析返回空响应");
        } else {
            log.warn("catalog 软件类型解析失败 reason={}", exception.getMessage());
        }
        return new BusinessException(
                ErrorCode.SOFTWARE_TYPE_LOOKUP_FAILED, ErrorMessages.SOFTWARE_TYPE_LOOKUP_FAILED);
    }

    private SoftwareType toDomain(SoftwareTypeResponse response) {
        SoftwareType type = new SoftwareType();
        type.setId(response.getId());
        type.setCategory(response.getCategory());
        type.setName(response.getName());
        type.setDescription(response.getDescription());
        type.setActive(response.isActive());
        type.setCreatedAt(response.getCreatedAt());
        type.setUpdatedAt(response.getUpdatedAt());
        return type;
    }
}
