package com.middleware.network;

import com.middleware.manager.config.StorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.middleware.network", "com.middleware.manager"})
@EnableDiscoveryClient
@EnableAsync
@MapperScan("com.middleware.manager.repository")
@EnableConfigurationProperties(StorageProperties.class)
public class NetworkServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetworkServiceApplication.class, args);
    }
}
