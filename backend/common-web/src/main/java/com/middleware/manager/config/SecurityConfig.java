package com.middleware.manager.config;

import com.middleware.manager.security.GatewayHeaderAuthenticationFilter;
import com.middleware.manager.service.CatalogSoftwareTypeProtocol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    static final String DEFAULT_CORS_ALLOWED_ORIGINS = String.join(",",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:8080",
            "http://127.0.0.1:8080");

    static {
        // 允许异步线程继承 SecurityContext
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    private final GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter;

    public SecurityConfig(GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter) {
        this.gatewayHeaderAuthenticationFilter = gatewayHeaderAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .addFilterBefore(gatewayHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/health").permitAll()
                        // 文件下载与图片：对所有人公开，无需登录
                        .requestMatchers("/files/**").permitAll()
                        // 论坛：个人中心需认证，其余读公开，写需登录
                        .requestMatchers("/api/forum/my-posts", "/api/forum/my-tags", "/api/forum/my-tags/**").authenticated()
                        .requestMatchers("/api/forum/admin/**").hasAnyRole("SYS_ADMIN", "MIDDLEWARE_ADMIN",
                                "DATABASE_ADMIN", "HOST_ADMIN", "NETWORK_ADMIN", "SECURITY_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/forum/**").permitAll()
                        .requestMatchers("/api/forum/**").authenticated()
                        // 公开接口
                        .requestMatchers("/api/public/**").permitAll()
                        // 常用命令：导入导出仅系统管理员，其他读公开、写需登录
                        .requestMatchers(HttpMethod.GET, "/api/middleware-commands/export").hasRole("SYS_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/middleware-commands/import").hasRole("SYS_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/middleware-commands/**").permitAll()
                        .requestMatchers("/api/middleware-commands/**").authenticated()
                        // 登录接口公开
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // 仅由控制器内的网关 HMAC 校验保护
                        .requestMatchers(HttpMethod.POST, "/api/auth/introspect").permitAll()
                        // catalog 内部类型解析接口由控制器校验服务间 HMAC
                        .requestMatchers(HttpMethod.POST,
                                CatalogSoftwareTypeProtocol.BASE_PATH + CatalogSoftwareTypeProtocol.BY_IDS_PATH,
                                CatalogSoftwareTypeProtocol.BASE_PATH + CatalogSoftwareTypeProtocol.BY_CATEGORY_PATH,
                                CatalogSoftwareTypeProtocol.BASE_PATH + CatalogSoftwareTypeProtocol.ACTIVE_PATH,
                                CatalogSoftwareTypeProtocol.BASE_PATH + CatalogSoftwareTypeProtocol.RESOLVE_PATH)
                        .permitAll()
                        // 其他 auth 接口需认证
                        .requestMatchers("/api/auth/**").authenticated()
                        // 用户管理：仅系统管理员
                        .requestMatchers("/api/admin/users/**").hasRole("SYS_ADMIN")
                        // 管理后台：系统管理员+专业管理员+管理岗
                        .requestMatchers("/api/admin/**").hasAnyRole("SYS_ADMIN",
                                "MIDDLEWARE_ADMIN", "DATABASE_ADMIN", "HOST_ADMIN", "NETWORK_ADMIN", "SECURITY_ADMIN",
                                "MIDDLEWARE_MGR", "DATABASE_MGR", "HOST_MGR", "NETWORK_MGR", "SECURITY_MGR")
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String origins = System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", DEFAULT_CORS_ALLOWED_ORIGINS);
        CorsConfiguration configuration = buildCorsConfiguration(origins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/files/**", configuration);
        return source;
    }

    static CorsConfiguration buildCorsConfiguration(String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition"));
        configuration.setAllowCredentials(true);
        return configuration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
