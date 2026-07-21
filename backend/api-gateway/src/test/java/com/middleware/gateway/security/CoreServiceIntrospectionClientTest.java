package com.middleware.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class CoreServiceIntrospectionClientTest {

    @Test
    @DisplayName("TC-GATEWAY-009 introspect 请求调用 core-service 并携带 Token HMAC")
    void introspectionRequestCarriesTokenSignature() {
        GatewaySignatureService signatureService =
                new GatewaySignatureService("test-only-gateway-signing-secret");
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"valid\":false,\"roles\":[],\"categoryAdmin\":false}")
                    .build());
        };
        WebClient.Builder direct = WebClient.builder().exchangeFunction(exchangeFunction);
        WebClient.Builder loadBalanced = WebClient.builder().exchangeFunction(exchangeFunction);
        CoreServiceIntrospectionClient client = new CoreServiceIntrospectionClient(
                direct, loadBalanced, signatureService, "http://core-service", false);

        GatewayIntrospectionResult response = client.introspect("valid-token").block();

        assertThat(response).isNotNull();
        assertThat(captured.get().url().toString())
                .isEqualTo("http://core-service/api/auth/introspect");
        assertThat(captured.get().headers().getFirst(GatewayIdentityHeaders.SIGNATURE))
                .isEqualTo(signatureService.signIntrospectionToken("valid-token"));
    }
}
