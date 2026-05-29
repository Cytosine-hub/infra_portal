package com.middleware.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")))
                .authorizeHttpRequests(auth -> auth
                        // 图片文件公开访问（markdown 预览中 img 标签无 Auth header）
                        .requestMatchers("/files/images/**").permitAll()
                        // 文件下载：需登录
                        .requestMatchers("/files/**").authenticated()
                        // 论坛：个人中心需认证，其余读公开，写需登录
                        .requestMatchers("/api/forum/my-posts").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/forum/**").permitAll()
                        .requestMatchers("/api/forum/**").authenticated()
                        // 公开接口
                        .requestMatchers("/api/public/**").permitAll()
                        // 常用命令：读公开，写需登录
                        .requestMatchers(HttpMethod.GET, "/api/middleware-commands/**").permitAll()
                        .requestMatchers("/api/middleware-commands/**").authenticated()
                        // 登录
                        .requestMatchers("/api/auth/**").authenticated()
                        // 用户管理：仅系统管理员
                        .requestMatchers("/api/admin/users/**").hasRole("SYS_ADMIN")
                        // 管理后台：系统管理员+专业管理员+管理岗
                        .requestMatchers("/api/admin/**").hasAnyRole("SYS_ADMIN",
                                "MIDDLEWARE_ADMIN", "DATABASE_ADMIN", "HOST_ADMIN", "NETWORK_ADMIN", "SECURITY_ADMIN",
                                "MIDDLEWARE_MGR", "DATABASE_MGR", "HOST_MGR", "NETWORK_MGR", "SECURITY_MGR")
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase())));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/files/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
