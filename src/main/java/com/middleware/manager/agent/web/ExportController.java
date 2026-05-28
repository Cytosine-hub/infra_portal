package com.middleware.manager.agent.web;

import com.middleware.manager.agent.export.ExcelExportService;
import com.middleware.manager.agent.zabbix.ZabbixClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("opsExportController")
@RequestMapping("/api/ops-agent/export")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ZabbixClient zabbixClient;
    private final ExcelExportService excelExportService;

    public ExportController(ZabbixClient zabbixClient, ExcelExportService excelExportService) {
        this.zabbixClient = zabbixClient;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/zabbix")
    public ResponseEntity<byte[]> exportZabbixData(
            @RequestParam String host,
            @RequestParam(required = false) String metric,
            @RequestParam(defaultValue = "1h") String timeRange,
            @RequestParam(defaultValue = "1000") int limit) {

        try {
            log.info("Exporting Zabbix data: host={}, metric={}, timeRange={}, limit={}", host, metric, timeRange, limit);

            List<Map<String, Object>> data = zabbixClient.queryMetrics(host, metric, timeRange, limit);

            if (data.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            String title = host + " 监控数据";
            byte[] excelBytes = excelExportService.exportZabbixData(data, title);

            String filename = URLEncoder.encode(host + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx",
                    StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelBytes.length)
                    .body(excelBytes);
        } catch (Exception e) {
            log.error("Failed to export Zabbix data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/zabbix/batch")
    public ResponseEntity<byte[]> exportMultipleHosts(@RequestBody BatchExportRequest request) {
        try {
            log.info("Batch exporting Zabbix data for hosts: {}", request.getHosts());

            Map<String, List<Map<String, Object>>> allData = new LinkedHashMap<>();

            for (String host : request.getHosts()) {
                List<Map<String, Object>> data = zabbixClient.queryMetrics(
                        host,
                        request.getMetric(),
                        request.getTimeRange(),
                        request.getLimit()
                );
                if (!data.isEmpty()) {
                    allData.put(host, data);
                }
            }

            if (allData.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            byte[] excelBytes = excelExportService.exportMultipleHosts(allData);

            String filename = URLEncoder.encode("zabbix_batch_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx",
                    StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(excelBytes.length)
                    .body(excelBytes);
        } catch (Exception e) {
            log.error("Failed to batch export Zabbix data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class BatchExportRequest {
        private List<String> hosts;
        private String metric;
        private String timeRange = "1h";
        private int limit = 1000;

        public List<String> getHosts() {
            return hosts;
        }

        public void setHosts(List<String> hosts) {
            this.hosts = hosts;
        }

        public String getMetric() {
            return metric;
        }

        public void setMetric(String metric) {
            this.metric = metric;
        }

        public String getTimeRange() {
            return timeRange;
        }

        public void setTimeRange(String timeRange) {
            this.timeRange = timeRange;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}
