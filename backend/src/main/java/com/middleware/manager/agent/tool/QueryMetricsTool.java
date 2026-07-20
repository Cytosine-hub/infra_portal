package com.middleware.manager.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryMetricsTool implements Tool {

    @Value("${app.agent.metrics.url:}")
    private String metricsUrl;

    @Override
    public String name() { return "query_metrics"; }

    @Override
    public String description() {
        return "查询监控指标（Prometheus）。参数：promql(PromQL查询语句), start(开始时间), end(结束时间)";
    }

    @Override
    public String call(Map<String, Object> params) {
        if (metricsUrl == null || metricsUrl.isEmpty()) {
            return "监控系统未配置，请在 app.agent.metrics.url 中配置 Prometheus 地址";
        }
        // TODO: 实现 Prometheus API 调用
        return "监控查询功能待接入 Prometheus 后实现";
    }
}
