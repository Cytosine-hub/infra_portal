package com.middleware.manager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.CatalogSoftwareTypeProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteSoftwareTypeLookupTest {
    private static final String SECRET = "test-only-gateway-signing-secret";

    @Test
    @DisplayName("TC-COMMAND-003 远程 SoftwareTypeLookup 按名解析并携带服务间 HMAC")
    void resolveOrCreateCallsCatalogWithSignedNames() {
        GatewaySignatureService signatureService = new GatewaySignatureService(SECRET);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String payload = CatalogSoftwareTypeProtocol.resolvePayload("中间件", "Redis");
        server.expect(requestTo("http://core-service/api/internal/catalog/software-types/resolve"))
                .andExpect(header(GatewayIdentityHeaders.SIGNATURE,
                        signatureService.signInternalRequest(
                                CatalogSoftwareTypeProtocol.RESOLVE_OPERATION, payload)))
                .andExpect(jsonPath("$.categoryName").value("中间件"))
                .andExpect(jsonPath("$.softwareTypeName").value("Redis"))
                .andRespond(withSuccess("""
                        {"id":88,"category":"中间件","name":"Redis","active":true}
                        """, MediaType.APPLICATION_JSON));
        RemoteSoftwareTypeLookup lookup = new RemoteSoftwareTypeLookup(
                builder.build(), signatureService);

        SoftwareType result = lookup.resolveOrCreate("中间件", "Redis");

        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getCategory()).isEqualTo("中间件");
        assertThat(result.getName()).isEqualTo("Redis");
        server.verify();
    }

    @Test
    @DisplayName("TC-COMMAND-004 远程 SoftwareTypeLookup 携带服务间 HMAC 获取全部启用类型")
    void findActiveCallsCatalogWithSignature() {
        GatewaySignatureService signatureService = new GatewaySignatureService(SECRET);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://core-service/api/internal/catalog/software-types/active"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(GatewayIdentityHeaders.SIGNATURE,
                        signatureService.signInternalRequest(
                                CatalogSoftwareTypeProtocol.ACTIVE_OPERATION, "")))
                .andRespond(withSuccess("""
                        [{"id":8,"category":"中间件","name":"Kafka","active":true}]
                        """, MediaType.APPLICATION_JSON));
        RemoteSoftwareTypeLookup lookup = new RemoteSoftwareTypeLookup(
                builder.build(), signatureService);

        assertThat(lookup.findActive()).extracting(SoftwareType::getName).containsExactly("Kafka");
        server.verify();
    }
}
