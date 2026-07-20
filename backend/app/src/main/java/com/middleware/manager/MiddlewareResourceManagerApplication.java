package com.middleware.manager;

import com.middleware.manager.config.ModuleProperties;
import com.middleware.manager.config.StorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan({
        "com.middleware.manager.repository",
        "com.middleware.manager.wiki.repository",
        "com.middleware.manager.knowledge.agent",
        "com.middleware.manager.knowledge.repository",
        "com.middleware.manager.agent.repository"
})
@EnableConfigurationProperties({StorageProperties.class, ModuleProperties.class})
public class MiddlewareResourceManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiddlewareResourceManagerApplication.class, args);
    }
}
