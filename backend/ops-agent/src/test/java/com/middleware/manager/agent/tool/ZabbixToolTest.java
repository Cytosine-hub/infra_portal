package com.middleware.manager.agent.tool;

import com.middleware.manager.agent.zabbix.ZabbixClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZabbixToolTest {

    @Mock
    private ZabbixClient zabbixClient;

    private ZabbixTool zabbixTool;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        zabbixTool = new ZabbixTool(zabbixClient);
    }

    @Test
    void testName() {
        assertEquals("zabbix_query", zabbixTool.name());
    }

    @Test
    void testDescription() {
        assertNotNull(zabbixTool.description());
        assertTrue(zabbixTool.description().contains("Zabbix"));
    }

    @Test
    void testCallWithMissingHost() {
        Map<String, Object> params = new HashMap<>();
        String result = zabbixTool.call(params);
        assertTrue(result.contains("错误"));
        assertTrue(result.contains("主机名"));
    }

    @Test
    void testCallWithEmptyHost() {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "");
        String result = zabbixTool.call(params);
        assertTrue(result.contains("错误"));
    }

    @Test
    void testCallSuccess() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "web-server-01");
        params.put("metric", "cpu");
        params.put("timeRange", "1h");
        params.put("limit", 10);

        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> record = new HashMap<>();
        record.put("host", "web-server-01");
        record.put("metric", "CPU Usage");
        record.put("key", "system.cpu.util[,idle]");
        record.put("units", "%");
        record.put("value", "45.2");
        record.put("clock", System.currentTimeMillis() / 1000);
        mockData.add(record);

        when(zabbixClient.queryMetrics(eq("web-server-01"), eq("cpu"), eq("1h"), eq(10)))
                .thenReturn(mockData);

        String result = zabbixTool.call(params);
        assertNotNull(result);
        assertTrue(result.contains("查询结果"));
        assertTrue(result.contains("1条"));
        assertTrue(result.contains("CPU Usage"));
    }

    @Test
    void testCallWithNoData() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "web-server-01");

        when(zabbixClient.queryMetrics(eq("web-server-01"), isNull(), eq("1h"), eq(100)))
                .thenReturn(Collections.emptyList());

        String result = zabbixTool.call(params);
        assertTrue(result.contains("未找到"));
    }

    @Test
    void testCallWithException() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "web-server-01");

        when(zabbixClient.queryMetrics(anyString(), any(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = zabbixTool.call(params);
        assertTrue(result.contains("失败"));
        assertTrue(result.contains("Connection refused"));
    }
}
