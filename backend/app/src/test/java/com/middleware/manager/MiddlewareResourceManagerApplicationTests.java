package com.middleware.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MiddlewareResourceManagerApplicationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("TC-APP-001 默认 profile 加载上下文且不启用 Nacos")
    void contextLoads() {
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .isFalse();
    }

    @Test
    @DisplayName("TC-APP-002 app 类路径不再包含论坛 Controller")
    void appClasspathDoesNotContainForumController() {
        assertThat(MiddlewareResourceManagerApplication.class.getClassLoader()
                .getResource("com/middleware/manager/web/api/ForumController.class"))
                .isNull();
    }

    @Test
    @DisplayName("TC-APP-003 app 类路径不再包含 AI 集群 Controller")
    void appClasspathDoesNotContainAiControllers() {
        ClassLoader classLoader = MiddlewareResourceManagerApplication.class.getClassLoader();

        assertThat(classLoader.getResource(
                "com/middleware/manager/knowledge/web/KnowledgeController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/knowledge/web/KnowledgeGraphController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/knowledge/web/AgentController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/wiki/web/WikiController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/agent/web/AgentController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/agent/web/ExportController.class")).isNull();
    }

    @Test
    @DisplayName("TC-APP-004 app 类路径不再包含 AI 专属重依赖")
    void appClasspathDoesNotContainAiOnlyDependencies() {
        ClassLoader classLoader = MiddlewareResourceManagerApplication.class.getClassLoader();

        assertThat(classLoader.getResource("io/milvus/client/MilvusServiceClient.class")).isNull();
        assertThat(classLoader.getResource("dev/langchain4j/model/chat/ChatModel.class")).isNull();
        assertThat(classLoader.getResource("org/jgrapht/Graph.class")).isNull();
        assertThat(classLoader.getResource("com/fasterxml/jackson/dataformat/yaml/YAMLFactory.class")).isNull();
    }

    @Test
    @DisplayName("TC-APP-005 app 类路径不再包含 identity、catalog、standards Controller")
    void appClasspathDoesNotContainCoreControllers() {
        ClassLoader classLoader = MiddlewareResourceManagerApplication.class.getClassLoader();
        String[] controllers = {
            "AdminAccountApiController",
            "AdminParameterStandardController",
            "AdminReleaseApiController",
            "AdminSettingController",
            "AdminSoftwareCategoryApiController",
            "AdminSoftwareTypeApiController",
            "AdminStandardDocumentApiController",
            "AdminStandardParameterApiController",
            "AdminUserController",
            "AuthApiController",
            "DocumentRevisionController",
            "ImageController",
            "PublicConfigController",
            "PublicParameterStandardController",
            "PublicReleaseApiController",
            "PublicStandardDocumentApiController",
            "PublicStandardParameterApiController",
            "ReviewApiController"
        };

        for (String controller : controllers) {
            assertThat(classLoader.getResource(
                    "com/middleware/manager/web/api/" + controller + ".class")).isNull();
        }
        assertThat(classLoader.getResource(
                "com/middleware/manager/web/controller/FileDownloadController.class")).isNull();
    }

    @Test
    @DisplayName("TC-APP-006 app 仍加载岗位中间件命令 Controller")
    void appStillLoadsMiddlewareJobController() {
        assertThat(applicationContext.getBean(
                com.middleware.manager.web.api.MiddlewareCommandApiController.class)).isNotNull();
    }

    @Test
    @DisplayName("TC-APP-007 app 不再包含 TokenService 且伪造管理员头无签名返回 401")
    void appRejectsUnsignedIdentityWithoutTokenDatabaseAuthentication() throws Exception {
        assertThat(MiddlewareResourceManagerApplication.class.getClassLoader()
                .getResource("com/middleware/manager/service/TokenService.class"))
                .isNull();

        mockMvc.perform(post("/api/middleware-commands")
                        .header(GatewayIdentityHeaders.USER, "mallory")
                        .header(GatewayIdentityHeaders.ROLES, "ROLE_SYS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
