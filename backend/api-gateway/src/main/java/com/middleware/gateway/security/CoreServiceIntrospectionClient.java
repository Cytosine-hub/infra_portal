package com.middleware.gateway.security;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class CoreServiceIntrospectionClient implements GatewayIntrospectionClient {
    private static final String INTROSPECTION_PATH = "/api/auth/introspect";

    private final WebClient webClient;
    private final GatewaySignatureService signatureService;

    public CoreServiceIntrospectionClient(
            @Qualifier("gatewayDirectWebClientBuilder") WebClient.Builder directBuilder,
            @Qualifier("gatewayLoadBalancedWebClientBuilder") WebClient.Builder loadBalancedBuilder,
            GatewaySignatureService signatureService,
            @Value("${app.security.introspection-base-url}") String introspectionBaseUrl,
            @Value("${app.security.introspection-load-balanced:false}") boolean loadBalanced) {
        WebClient.Builder builder = loadBalanced ? loadBalancedBuilder : directBuilder;
        this.webClient = builder.baseUrl(introspectionBaseUrl).build();
        this.signatureService = signatureService;
    }

    @Override
    public Mono<GatewayIntrospectionResult> introspect(String token) {
        return webClient.post()
                .uri(INTROSPECTION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(GatewayIdentityHeaders.SIGNATURE,
                        signatureService.signIntrospectionToken(token))
                .bodyValue(new IntrospectionRequest(token))
                .retrieve()
                .bodyToMono(GatewayIntrospectionResult.class);
    }

    private record IntrospectionRequest(String token) {
    }
}
