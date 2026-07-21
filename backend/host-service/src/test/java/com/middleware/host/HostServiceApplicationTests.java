package com.middleware.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HostServiceApplicationTests {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("TC-HOST-001 默认 profile 独立加载主机岗位且关闭 Nacos")
    void defaultProfileLoadsWithNacosDisabled() {
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8087);
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("host-service");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("TC-HOST-002 GET /health 无需认证")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
