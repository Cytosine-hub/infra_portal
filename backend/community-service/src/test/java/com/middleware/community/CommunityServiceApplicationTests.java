package com.middleware.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.pagehelper.PageInfo;
import com.middleware.manager.config.ApiAuditLogger;
import com.middleware.manager.domain.ForumPost;
import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.ForumService;
import com.middleware.manager.web.api.ForumController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CommunityServiceApplicationTests.ForumTestConfiguration.class)
class CommunityServiceApplicationTests {

    private static final GatewaySignatureService SIGNATURE_SERVICE =
            new GatewaySignatureService("test-only-gateway-signing-secret");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("TC-COMMUNITY-001 默认 profile 独立加载论坛且关闭 Nacos")
    void defaultProfileLoadsCommunityOnlyWithNacosDisabled() {
        assertThat(applicationContext.getBean(ForumController.class)).isNotNull();
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8082);
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("community-service");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("TC-COMMUNITY-002 GET /api/forum/posts 保持公开")
    void forumReadRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/forum/posts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-COMMUNITY-003 POST /api/forum/posts 未认证返回 401")
    void forumWriteRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/forum/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"content\":\"content\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-COMMUNITY-004 GET /api/forum/my-posts 未认证返回 401")
    void myPostsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/forum/my-posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-COMMUNITY-005 正确签名身份头可访问论坛写接口")
    void validGatewayIdentityAuthenticatesForumWrite() throws Exception {
        mockMvc.perform(post("/api/forum/posts")
                        .headers(signedIdentityHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"content\":\"content\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-COMMUNITY-006 直连伪造管理员身份头但无签名返回 401")
    void forgedAdminHeadersWithoutSignatureAreRejected() throws Exception {
        mockMvc.perform(post("/api/forum/posts")
                        .header(GatewayIdentityHeaders.USER, "mallory")
                        .header(GatewayIdentityHeaders.ROLES, "ROLE_SYS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"content\":\"content\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-COMMUNITY-007 community-service 类路径不再包含 TokenService")
    void communityServiceDoesNotContainTokenValidationService() {
        assertThat(CommunityServiceApplication.class.getClassLoader()
                .getResource("com/middleware/manager/service/TokenService.class"))
                .isNull();
    }

    private HttpHeaders signedIdentityHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(GatewayIdentityHeaders.USER, "alice");
        headers.set(GatewayIdentityHeaders.DISPLAY_NAME, "Alice");
        headers.set(GatewayIdentityHeaders.ROLES, "ROLE_DEV_MGR");
        headers.set(GatewayIdentityHeaders.CATEGORY, "");
        headers.set(GatewayIdentityHeaders.CATEGORY_ADMIN, "false");
        headers.set(GatewayIdentityHeaders.SIGNATURE,
                SIGNATURE_SERVICE.signIdentityHeaders(
                        "alice", "Alice", "ROLE_DEV_MGR", "", "false"));
        return headers;
    }

    @TestConfiguration
    static class ForumTestConfiguration {

        @Bean
        @Primary
        ForumService stubForumService() {
            return new ForumService(null, null, null, null) {
                @Override
                public PageInfo<ForumPost> listPosts(String keyword, String tag, String job, int page, int size) {
                    return new PageInfo<>(List.of());
                }

                @Override
                public ForumPost createPost(String title, String content, List<String> tagNames,
                                            String authorUsername, String authorDisplayName) {
                    ForumPost post = new ForumPost();
                    post.setId(1L);
                    post.setTitle(title);
                    post.setContent(content);
                    post.setAuthorUsername(authorUsername);
                    post.setAuthorDisplayName(authorDisplayName);
                    return post;
                }
            };
        }

        @Bean
        @Primary
        ApiAuditLogger noopApiAuditLogger() {
            return (username, method, path, queryString, statusCode, ipAddress, userAgent, durationMs) -> { };
        }

        @Bean
        @Primary
        PlatformTransactionManager noopTransactionManager() {
            return new PlatformTransactionManager() {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return new SimpleTransactionStatus();
                }

                @Override
                public void commit(TransactionStatus status) {
                }

                @Override
                public void rollback(TransactionStatus status) {
                }
            };
        }
    }
}
