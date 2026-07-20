package com.middleware.manager.agent.tool;

import com.middleware.manager.agent.export.ExcelExportService;
import com.middleware.manager.agent.zabbix.ZabbixClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ZabbixExportTool implements Tool {

    private final ZabbixClient zabbixClient;
    private final ExcelExportService excelExportService;

    public ZabbixExportTool(ZabbixClient zabbixClient, ExcelExportService excelExportService) {
        this.zabbixClient = zabbixClient;
        this.excelExportService = excelExportService;
    }

    @Override
    public String name() {
        return "zabbix_export";
    }

    @Override
    public String description() {
        return "导出 Zabbix 监控数据为 Excel 文件。参数：host(主机名), metric(指标关键字), timeRange(时间范围), limit(返回条数)";
    }

    @Override
    public String call(Map<String, Object> params) {
        try {
            String host = (String) params.get("host");
            String metric = (String) params.get("metric");
            String timeRange = (String) params.getOrDefault("timeRange", "1h");
            int limit = params.containsKey("limit") ? Integer.parseInt(params.get("limit").toString()) : 1000;

            if (host == null || host.isEmpty()) {
                return "错误：请指定主机名（host 参数）";
            }

            log.info("Exporting Zabbix data to Excel: host={}, metric={}, timeRange={}, limit={}", host, metric, timeRange, limit);

            List<Map<String, Object>> data = zabbixClient.queryMetrics(host, metric, timeRange, limit);

            if (data.isEmpty()) {
                return "未找到匹配的监控数据，无法导出。";
            }

            byte[] excelBytes = excelExportService.exportZabbixData(data, host + " 监控数据");

            // 保存到临时文件
            String filename = host + "_monitoring_data.xlsx";
            java.nio.file.Path tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"));
            java.nio.file.Path filePath = tempDir.resolve(filename);
            java.nio.file.Files.write(filePath, excelBytes);

            return String.format("✅ 已成功导出 %d 条监控数据到 Excel 文件\n\n文件路径: %s\n文件大小: %.2f KB",
                    data.size(), filePath.toAbsolutePath(), excelBytes.length / 1024.0);
        } catch (Exception e) {
            log.error("Failed to export Zabbix data to Excel", e);
            return "导出 Excel 失败: " + e.getMessage();
        }
    }
}
