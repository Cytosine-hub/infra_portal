package com.middleware.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.service.StorageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * core-service 聚合 catalog 与 standards 两个业务库后的限流回归测试。
 * mock 掉会访问数据库的 Service/StorageService，只验证限流拦截器在合并后的 Spring 上下文里
 * 是否同时正确注册并生效，避免依赖真实 MySQL 连接或磁盘文件。
 */
@SpringBootTest(
        classes = CoreServiceApplication.class,
        properties = {
            "app.rate-limit.download.limit=2",
            "app.rate-limit.download.window-seconds=60",
            "app.rate-limit.document.limit=2",
            "app.rate-limit.document.window-seconds=60"
        })
@AutoConfigureMockMvc
class RateLimitAggregationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReleaseService releaseService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private StandardDocumentService standardDocumentService;

    @Test
    @DisplayName("TC-02/TC-06 core-service 同时聚合 catalog 与 standards 后，下载与标准文档访问限流均生效互不覆盖")
    void bothDownloadAndDocumentRateLimitsApplyInAggregatedContext() throws Exception {
        ReleaseAsset release = new ReleaseAsset();
        release.setOriginalFileName("rate-limit-agg.bin");
        release.setStoredFileName("rate-limit-agg.bin");
        release.setFileSize(4);
        when(releaseService.getPublishedRelease(anyString())).thenReturn(release);
        when(storageService.loadAsResource(anyString()))
                .thenReturn(new ByteArrayResource("test".getBytes(StandardCharsets.UTF_8)));

        StandardDocument document = new StandardDocument();
        document.setId(1L);
        document.setStatus("PUBLISHED");
        when(standardDocumentService.get(anyLong())).thenReturn(document);
        when(standardDocumentService.render(any())).thenReturn("");

        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(get("/files/rate-limit-agg-token-" + i)).andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("下载接口未超阈值时不应被限流")
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        mockMvc.perform(get("/files/rate-limit-agg-token-over-limit"))
                .andExpect(status().isTooManyRequests());

        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(get("/api/public/standards/" + (900001 + i))).andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("标准文档访问接口未超阈值时不应被限流")
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        mockMvc.perform(get("/api/public/standards/900099"))
                .andExpect(status().isTooManyRequests());
    }
}
