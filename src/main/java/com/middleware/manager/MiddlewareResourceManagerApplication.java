package com.middleware.manager;

import com.middleware.manager.config.ModuleProperties;
import com.middleware.manager.config.StorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.middleware.manager.repository")
@EnableConfigurationProperties({StorageProperties.class, ModuleProperties.class})
public class MiddlewareResourceManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiddlewareResourceManagerApplication.class, args);
    }
}
