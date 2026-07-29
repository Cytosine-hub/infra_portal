package com.middleware.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.security.gateway.IdentityHeaderCodec;
import com.middleware.manager.web.api.MiddlewareCommandApiController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MiddlewareServiceApplicationTests {

    private static final GatewaySignatureService SIGNATURE_SERVICE =
            new GatewaySignatureService("test-only-gateway-signing-secret");

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("TC-MIDDLEWARE-001 默认 profile 独立加载中间件岗位且关闭 Nacos")
    void defaultProfileLoadsMiddlewareOnlyWithNacosDisabled() {
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8085);
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("middleware-service");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("TC-MIDDLEWARE-002 middleware-service 类路径不含论坛 Controller")
    void classpathDoesNotContainForumController() {
        assertThat(MiddlewareServiceApplication.class.getClassLoader()
                .getResource("com/middleware/manager/web/api/ForumController.class"))
                .isNull();
    }

    @Test
    @DisplayName("TC-MIDDLEWARE-003 middleware-service 类路径不含 AI 集群 Controller")
    void classpathDoesNotContainAiControllers() {
        ClassLoader classLoader = MiddlewareServiceApplication.class.getClassLoader();

        assertThat(classLoader.getResource(
                "com/middleware/manager/knowledge/web/KnowledgeController.class")).isNull();
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
    @DisplayName("TC-MIDDLEWARE-004 middleware-service 类路径不含 AI 专属重依赖")
    void classpathDoesNotContainAiOnlyDependencies() {
        ClassLoader classLoader = MiddlewareServiceApplication.class.getClassLoader();

        assertThat(classLoader.getResource("io/milvus/client/MilvusServiceClient.class")).isNull();
        assertThat(classLoader.getResource("dev/langchain4j/model/chat/ChatModel.class")).isNull();
        assertThat(classLoader.getResource("org/jgrapht/Graph.class")).isNull();
        assertThat(classLoader.getResource("com/fasterxml/jackson/dataformat/yaml/YAMLFactory.class")).isNull();
    }

    @Test
    @DisplayName("TC-MIDDLEWARE-005 middleware-service 类路径不含平台核心 Controller")
    void classpathDoesNotContainCoreControllers() {
        ClassLoader classLoader = MiddlewareServiceApplication.class.getClassLoader();
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
    @DisplayName("TC-MIDDLEWARE-006 middleware-service 加载原中间件命令 Controller")
    void loadsMiddlewareJobController() {
        assertThat(applicationContext.getBean(MiddlewareCommandApiController.class)).isNotNull();
    }

    @Test
    @DisplayName("TC-MIDDLEWARE-007 无签名伪造管理员头访问写端点返回 401")
    void rejectsUnsignedIdentityWithoutTokenDatabaseAuthentication() throws Exception {
        assertThat(MiddlewareServiceApplication.class.getClassLoader()
                .getResource("com/middleware/manager/service/TokenService.class"))
                .isNull();

        mockMvc.perform(post("/api/middleware-commands")
                        .header(GatewayIdentityHeaders.USER, "mallory")
                        .header(GatewayIdentityHeaders.ROLES, "ROLE_SYS_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-MIDDLEWARE-008 未认证访问命令导出端点返回 401")
    void commandExportRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/middleware-commands/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-MIDDLEWARE-009 岗位管理员不能执行全量命令导入导出")
    void commandTransferRequiresSystemAdministrator() throws Exception {
        HttpHeaders headers = signedIdentityHeaders("ROLE_MIDDLEWARE_ADMIN", "中间件", true);

        mockMvc.perform(get("/api/middleware-commands/export").headers(headers))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/middleware-commands/import")
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }

    private HttpHeaders signedIdentityHeaders(String role, String category, boolean categoryAdmin) {
        String user = IdentityHeaderCodec.encode("alice");
        String displayName = IdentityHeaderCodec.encode("Alice");
        String encodedCategory = IdentityHeaderCodec.encode(category);
        String categoryAdminValue = Boolean.toString(categoryAdmin);
        HttpHeaders headers = new HttpHeaders();
        headers.set(GatewayIdentityHeaders.USER, user);
        headers.set(GatewayIdentityHeaders.DISPLAY_NAME, displayName);
        headers.set(GatewayIdentityHeaders.ROLES, role);
        headers.set(GatewayIdentityHeaders.CATEGORY, encodedCategory);
        headers.set(GatewayIdentityHeaders.CATEGORY_ADMIN, categoryAdminValue);
        headers.set(GatewayIdentityHeaders.SIGNATURE,
                SIGNATURE_SERVICE.signIdentityHeaders(
                        user, displayName, role, encodedCategory, categoryAdminValue));
        return headers;
    }
}
