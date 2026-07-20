package com.middleware.manager.agent.tool;

import com.middleware.manager.agent.zabbix.ZabbixClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ZabbixTool implements Tool {

    private final ZabbixClient zabbixClient;

    public ZabbixTool(ZabbixClient zabbixClient) {
        this.zabbixClient = zabbixClient;
    }

    @Override
    public String name() {
        return "zabbix_query";
    }

    @Override
    public String description() {
        return "查询 Zabbix 监控数据。参数：host(主机名), metric(指标关键字), timeRange(时间范围，如1h/1d), limit(返回条数)";
    }

    @Override
    public String call(Map<String, Object> params) {
        try {
            String host = (String) params.get("host");
            String metric = (String) params.get("metric");
            String timeRange = (String) params.getOrDefault("timeRange", "1h");
            int limit = params.containsKey("limit") ? Integer.parseInt(params.get("limit").toString()) : 100;

            if (host == null || host.isEmpty()) {
                return "错误：请指定主机名（host 参数）";
            }

            log.info("Querying Zabbix: host={}, metric={}, timeRange={}, limit={}", host, metric, timeRange, limit);

            List<Map<String, Object>> results = zabbixClient.queryMetrics(host, metric, timeRange, limit);

            if (results.isEmpty()) {
                return "未找到匹配的监控数据。请检查主机名和指标关键字是否正确。";
            }

            // 格式化输出
            StringBuilder sb = new StringBuilder();
            sb.append("查询结果（").append(results.size()).append("条）：\n\n");

            // 按指标分组
            Map<String, List<Map<String, Object>>> grouped = results.stream()
                    .collect(Collectors.groupingBy(r -> (String) r.get("metric")));

            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                sb.append("## ").append(entry.getKey()).append("\n");
                sb.append("主机: ").append(host).append("\n");
                sb.append("单位: ").append(entry.getValue().get(0).get("units")).append("\n\n");

                sb.append("| 时间 | 值 |\n");
                sb.append("|------|-----|\n");

                for (Map<String, Object> record : entry.getValue()) {
                    long clock = ((Number) record.get("clock")).longValue();
                    String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date(clock * 1000));
                    sb.append("| ").append(time).append(" | ").append(record.get("value")).append(" |\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to query Zabbix", e);
            return "查询 Zabbix 失败: " + e.getMessage();
        }
    }
}
