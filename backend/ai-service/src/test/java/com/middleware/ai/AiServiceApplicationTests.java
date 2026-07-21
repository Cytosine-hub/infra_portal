package com.middleware.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.middleware.manager.agent.web.ExportController;
import com.middleware.manager.knowledge.web.KnowledgeController;
import com.middleware.manager.knowledge.web.KnowledgeGraphController;
import com.middleware.manager.wiki.web.WikiController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AiServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("TC-AI-001 默认 profile 独立加载 AI 集群且关闭 Nacos")
    void defaultProfileLoadsAiClusterWithNacosDisabled() {
        assertThat(applicationContext.getBean(KnowledgeController.class)).isNotNull();
        assertThat(applicationContext.getBean(KnowledgeGraphController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.middleware.manager.knowledge.web.AgentController.class)).isNotNull();
        assertThat(applicationContext.getBean(WikiController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.middleware.manager.agent.web.AgentController.class)).isNotNull();
        assertThat(applicationContext.getBean(ExportController.class)).isNotNull();
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8083);
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("ai-service");
        assertThat(environment.getProperty(
                "spring.cloud.nacos.discovery.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty(
                "spring.cloud.nacos.config.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("TC-AI-002 AI 集群端点保持 Bearer Token 鉴权")
    void aiEndpointsRequireAuthentication() throws Exception {
        assertUnauthorized("/api/knowledge/search");
        assertUnauthorized("/api/agent/sessions");
        assertUnauthorized("/api/wiki/pages");
        assertUnauthorized("/api/ops-agent/sessions");
        assertUnauthorized("/api/ops-agent/export/zabbix");
    }

    @Test
    @DisplayName("TC-AI-003 ai-service 类路径不再包含 TokenService")
    void aiServiceDoesNotContainTokenValidationService() {
        assertThat(AiServiceApplication.class.getClassLoader()
                .getResource("com/middleware/manager/service/TokenService.class"))
                .isNull();
    }

    private void assertUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
