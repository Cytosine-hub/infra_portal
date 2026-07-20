package com.middleware.manager.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultipartConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            // 设置 Tomcat multipart 最大文件数量（默认很低，导致批量上传失败）
            connector.setMaxPartCount(200);
            connector.setMaxPostSize(-1);
        });
    }
}
