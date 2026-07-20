package com.middleware.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.pagehelper.PageInfo;
import com.middleware.manager.config.ApiAuditLogger;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.domain.ForumPost;
import com.middleware.manager.domain.RoleEntity;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.repository.RoleMapper;
import com.middleware.manager.repository.UserTokenMapper;
import com.middleware.manager.service.ForumService;
import com.middleware.manager.web.api.ForumController;
import java.time.LocalDateTime;
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
    @DisplayName("TC-COMMUNITY-005 有效 Bearer Token 可访问论坛写接口")
    void validSharedDatabaseTokenAuthenticatesForumWrite() throws Exception {
        mockMvc.perform(post("/api/forum/posts")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"content\":\"content\"}"))
                .andExpect(status().isOk());
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
        UserTokenMapper stubUserTokenMapper() {
            return new UserTokenMapper() {
                @Override
                public int insert(String token, String username, LocalDateTime expiresAt) {
                    return 1;
                }

                @Override
                public String findUsernameByToken(String token) {
                    return "valid-token".equals(token) ? "alice" : null;
                }

                @Override
                public int updateExpiry(String token, LocalDateTime expiresAt) {
                    return 1;
                }

                @Override
                public int deleteByToken(String token) {
                    return 1;
                }

                @Override
                public int deleteByUsername(String username) {
                    return 1;
                }

                @Override
                public int deleteExpired() {
                    return 0;
                }
            };
        }

        @Bean
        @Primary
        AdminAccountMapper stubAdminAccountMapper() {
            return new AdminAccountMapper() {
                @Override
                public AdminAccount findById(Long id) {
                    return account();
                }

                @Override
                public AdminAccount findByUsername(String username) {
                    return "alice".equals(username) ? account() : null;
                }

                @Override
                public List<AdminAccount> findAllByOrderByCreatedAtAsc() {
                    return List.of(account());
                }

                @Override
                public long countByRole(String role) {
                    return 1;
                }

                @Override
                public int insert(AdminAccount account) {
                    return 1;
                }

                @Override
                public int update(AdminAccount account) {
                    return 1;
                }

                @Override
                public int deleteById(Long id) {
                    return 1;
                }

                @Override
                public long count() {
                    return 1;
                }

                private AdminAccount account() {
                    AdminAccount account = new AdminAccount();
                    account.setId(1L);
                    account.setUsername("alice");
                    account.setPasswordHash("{noop}unused");
                    account.setRole("开发经理");
                    return account;
                }
            };
        }

        @Bean
        @Primary
        RoleMapper stubRoleMapper() {
            return new RoleMapper() {
                @Override
                public RoleEntity findById(Long id) {
                    return role();
                }

                @Override
                public RoleEntity findByDisplayName(String displayName) {
                    return role();
                }

                @Override
                public RoleEntity findByAuthority(String authority) {
                    return role();
                }

                @Override
                public List<RoleEntity> findAll() {
                    return List.of(role());
                }

                @Override
                public int insert(RoleEntity role) {
                    return 1;
                }

                @Override
                public int update(RoleEntity role) {
                    return 1;
                }

                @Override
                public int deleteById(Long id) {
                    return 1;
                }

                @Override
                public long count() {
                    return 1;
                }

                private RoleEntity role() {
                    RoleEntity role = new RoleEntity();
                    role.setId(1L);
                    role.setDisplayName("开发经理");
                    role.setAuthority("ROLE_DEV_MGR");
                    return role;
                }
            };
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
