package com.middleware.manager.knowledge.service;

import com.middleware.manager.repository.StandardParameterLookupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 参数标准的精确查询。
 * <p>「innodb_buffer_pool_size 标准值是多少」这类问题必须 100% 准确且可追责，
 * RAG 做不到——它只能给出语义相近的片段。这里直接查 standard_parameters 表，
 * 返回带标准版本号和发布时间的确定答案，绕开检索。
 */
class ParameterLookupServiceTest {

    @Mock private StandardParameterLookupMapper mapper;

    private ParameterLookupService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ParameterLookupService(mapper);
    }

    private ParameterAnswerRow answer(String code, String value) {
        ParameterAnswerRow a = new ParameterAnswerRow();
        a.setCode(code);
        a.setName(code);
        a.setValue(value);
        a.setStandardTitle("MySQL 8.0 参数标准");
        a.setStandardVersion("v2.3");
        return a;
    }

    @Test
    @DisplayName("TC-PARAM-001 按参数名精确查询应返回带标准版本的确定答案")
    void exactLookupReturnsAuthoritativeAnswer() {
        when(mapper.search("MySQL", "innodb_buffer_pool_size", 20))
                .thenReturn(List.of(answer("innodb_buffer_pool_size", "物理内存 70%")));

        List<ParameterLookupService.ParameterAnswer> result =
                service.lookup("MySQL", "innodb_buffer_pool_size", 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo("物理内存 70%");
        assertThat(result.get(0).getStandardVersion()).isEqualTo("v2.3");
    }

    @Test
    @DisplayName("TC-PARAM-002 软件与参数名都为空时应拒绝，避免全表返回")
    void rejectsEmptyQuery() {
        List<ParameterLookupService.ParameterAnswer> result = service.lookup("  ", null, 20);

        assertThat(result).isEmpty();
        verify(mapper, never()).search(any(), any(), any(Integer.class));
    }

    @Test
    @DisplayName("TC-PARAM-003 只给软件名应返回该软件的全部参数")
    void lookupBySoftwareOnly() {
        when(mapper.search("Redis", null, 20))
                .thenReturn(List.of(answer("maxmemory-policy", "allkeys-lru"),
                        answer("timeout", "300")));

        List<ParameterLookupService.ParameterAnswer> result = service.lookup("Redis", null, 20);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("TC-PARAM-004 limit 应被夹在合理范围，防止一次拉出整张表")
    void clampsLimit() {
        when(mapper.search("MySQL", null, 200)).thenReturn(List.of());

        service.lookup("MySQL", null, 100000);

        verify(mapper).search("MySQL", null, 200);
    }

    @Test
    @DisplayName("TC-PARAM-005 非正的 limit 应回落到默认值")
    void fallsBackToDefaultLimit() {
        when(mapper.search("MySQL", null, 20)).thenReturn(List.of());

        service.lookup("MySQL", null, 0);

        verify(mapper).search("MySQL", null, 20);
    }

    @Test
    @DisplayName("TC-PARAM-006 参数名两端空白应被裁掉，避免因误输空格查不到")
    void trimsWhitespace() {
        when(mapper.search("MySQL", "max_connections", 20)).thenReturn(List.of());

        service.lookup("  MySQL  ", "  max_connections  ", 20);

        verify(mapper).search("MySQL", "max_connections", 20);
    }
}
