package com.middleware.manager.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchLogsTool implements Tool {

    @Value("${app.agent.logs.url:}")
    private String logsUrl;

    @Override
    public String name() { return "search_logs"; }

    @Override
    public String description() {
        return "搜索应用日志（Elasticsearch/Loki）。参数：query(搜索关键词), service(服务名), timerange(时间范围如1h)";
    }

    @Override
    public String call(Map<String, Object> params) {
        if (logsUrl == null || logsUrl.isEmpty()) {
            return "日志系统未配置，请在 app.agent.logs.url 中配置 Elasticsearch/Loki 地址";
        }
        // TODO: 实现 ES/Loki API 调用
        return "日志查询功能待接入 Elasticsearch/Loki 后实现";
    }
}
