package com.middleware.ai.config;

import com.middleware.manager.integration.RemoteSoftwareTypeLookup;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.SoftwareTypeLookup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class CatalogClientConfiguration {

    @Bean
    @Primary
    @Qualifier("aiCatalogDirectRestClientBuilder")
    RestClient.Builder catalogDirectRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    @Qualifier("aiCatalogLoadBalancedRestClientBuilder")
    RestClient.Builder catalogLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    SoftwareTypeLookup softwareTypeLookup(
            @Qualifier("aiCatalogDirectRestClientBuilder") RestClient.Builder directBuilder,
            @Qualifier("aiCatalogLoadBalancedRestClientBuilder") RestClient.Builder loadBalancedBuilder,
            GatewaySignatureService signatureService,
            @Value("${app.catalog.base-url}") String catalogBaseUrl,
            @Value("${app.catalog.load-balanced:false}") boolean loadBalanced) {
        RestClient.Builder builder = loadBalanced ? loadBalancedBuilder : directBuilder;
        return new RemoteSoftwareTypeLookup(
                builder.clone().baseUrl(catalogBaseUrl).build(), signatureService);
    }
}
