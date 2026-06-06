package com.middleware.manager.agent.zabbix;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ZabbixClient {

    private static final Logger log = LoggerFactory.getLogger(ZabbixClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ZabbixConfig config;
    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private String authToken;
    private int requestId = 1;

    public ZabbixClient(ZabbixConfig config) {
        this.config = config;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .build();
    }

    public String login() throws IOException {
        JsonObject request = createRequest("user.login", Map.of(
                "username", config.getUsername(),
                "password", config.getPassword()
        ));

        JsonObject response = executeRequest(request);
        authToken = response.get("result").getAsString();
        log.info("Zabbix login successful");
        return authToken;
    }

    public JsonArray getHosts(String... hostIds) throws IOException {
        ensureAuthenticated();

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("selectInterfaces", new String[]{"ip", "type", "port"});

        if (hostIds != null && hostIds.length > 0) {
            params.put("hostids", hostIds);
        }

        JsonObject request = createRequest("host.get", params);
        JsonObject response = executeRequest(request);
        return response.getAsJsonArray("result");
    }

    public JsonArray getItems(String hostId, String... itemKeys) throws IOException {
        ensureAuthenticated();

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("hostids", hostId);
        params.put("sortfield", "name");

        if (itemKeys != null && itemKeys.length > 0) {
            params.put("search", Map.of("key_", String.join(",", itemKeys)));
        }

        JsonObject request = createRequest("item.get", params);
        JsonObject response = executeRequest(request);
        return response.getAsJsonArray("result");
    }

    public JsonArray getHistory(String itemId, int valueType, long timeFrom, long timeTill, int limit) throws IOException {
        ensureAuthenticated();

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("itemids", itemId);
        params.put("sortfield", "clock");
        params.put("sortorder", "DESC");
        params.put("limit", limit);
        params.put("history", valueType);

        if (timeFrom > 0) {
            params.put("time_from", timeFrom);
        }
        if (timeTill > 0) {
            params.put("time_till", timeTill);
        }

        JsonObject request = createRequest("history.get", params);
        JsonObject response = executeRequest(request);
        return response.getAsJsonArray("result");
    }

    public JsonArray getTrends(String itemId, long timeFrom, long timeTill) throws IOException {
        ensureAuthenticated();

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("itemids", itemId);
        params.put("sortfield", "clock");
        params.put("sortorder", "DESC");

        if (timeFrom > 0) {
            params.put("time_from", timeFrom);
        }
        if (timeTill > 0) {
            params.put("time_till", timeTill);
        }

        JsonObject request = createRequest("trend.get", params);
        JsonObject response = executeRequest(request);
        return response.getAsJsonArray("result");
    }

    public JsonArray getTriggers(String hostId, int limit) throws IOException {
        ensureAuthenticated();

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("hostids", hostId);
        params.put("sortfield", "lastchange");
        params.put("sortorder", "DESC");
        params.put("limit", limit);
        params.put("selectHosts", new String[]{"host", "name"});

        JsonObject request = createRequest("trigger.get", params);
        JsonObject response = executeRequest(request);
        return response.getAsJsonArray("result");
    }

    public JsonArray getAlerts(String hostId, int limit) throws IOException {
        ensureAuthenticated();

        Map<String, Object> params = new HashMap<>();
        params.put("output", "extend");
        params.put("hostids", hostId);
        params.put("sortfield", "clock");
        params.put("sortorder", "DESC");
        params.put("limit", limit);

        JsonObject request = createRequest("alert.get", params);
        JsonObject response = executeRequest(request);
        return response.getAsJsonArray("result");
    }

    public List<Map<String, Object>> queryMetrics(String hostName, String metricPattern,
                                                   String timeRange, int limit) throws IOException {
        // 1. Find host by name
        JsonArray hosts = getHosts();
        String hostId = null;
        for (JsonElement h : hosts) {
            JsonObject host = h.getAsJsonObject();
            if (host.get("host").getAsString().equalsIgnoreCase(hostName) ||
                host.get("name").getAsString().equalsIgnoreCase(hostName)) {
                hostId = host.get("hostid").getAsString();
                break;
            }
        }

        if (hostId == null) {
            throw new com.middleware.manager.exception.NotFoundException(com.middleware.manager.constant.ErrorCode.NOT_FOUND, "主机不存在");
        }

        // 2. Get items matching pattern
        JsonArray items = getItems(hostId);
        List<String> itemIds = new ArrayList<>();
        List<Map<String, String>> itemInfo = new ArrayList<>();

        for (JsonElement i : items) {
            JsonObject item = i.getAsJsonObject();
            String key = item.get("key_").getAsString();
            if (metricPattern == null || metricPattern.isEmpty() || key.contains(metricPattern)) {
                itemIds.add(item.get("itemid").getAsString());
                Map<String, String> info = new HashMap<>();
                info.put("itemid", item.get("itemid").getAsString());
                info.put("name", item.get("name").getAsString());
                info.put("key", key);
                info.put("units", item.get("units").getAsString());
                info.put("valueType", item.get("value_type").getAsString());
                itemInfo.add(info);
            }
        }

        // 3. Get history for each item
        long timeFrom = parseTimeRange(timeRange);
        long timeTill = System.currentTimeMillis() / 1000;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> info : itemInfo) {
            int valueType = Integer.parseInt(info.get("valueType"));
            JsonArray history = getHistory(info.get("itemid"), valueType, timeFrom, timeTill, limit);

            for (JsonElement h : history) {
                JsonObject record = h.getAsJsonObject();
                Map<String, Object> row = new HashMap<>();
                row.put("host", hostName);
                row.put("metric", info.get("name"));
                row.put("key", info.get("key"));
                row.put("units", info.get("units"));
                row.put("value", record.get("value").getAsString());
                row.put("clock", record.get("clock").getAsLong());
                result.add(row);
            }
        }

        return result;
    }

    private long parseTimeRange(String timeRange) {
        if (timeRange == null || timeRange.isEmpty()) {
            return System.currentTimeMillis() / 1000 - 3600; // 默认1小时
        }

        long now = System.currentTimeMillis() / 1000;
        timeRange = timeRange.trim().toLowerCase();

        if (timeRange.endsWith("h")) {
            int hours = Integer.parseInt(timeRange.replace("h", ""));
            return now - hours * 3600L;
        } else if (timeRange.endsWith("d")) {
            int days = Integer.parseInt(timeRange.replace("d", ""));
            return now - days * 86400L;
        } else if (timeRange.endsWith("m")) {
            int minutes = Integer.parseInt(timeRange.replace("m", ""));
            return now - minutes * 60L;
        }

        return now - 3600;
    }

    private synchronized void ensureAuthenticated() throws IOException {
        if (authToken == null) {
            login();
        }
    }

    private JsonObject createRequest(String method, Map<String, Object> params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", method);
        request.addProperty("id", requestId++);
        request.add("params", gson.toJsonTree(params));
        if (authToken != null) {
            request.addProperty("auth", authToken);
        }
        return request;
    }

    private JsonObject executeRequest(JsonObject request) throws IOException {
        String json = gson.toJson(request);
        log.debug("Zabbix request: {}", json);

        RequestBody body = RequestBody.create(json, JSON);
        Request httpRequest = new Request.Builder()
                .url(config.getUrl())
                .post(body)
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = response.body().string();
            log.debug("Zabbix response: {}", responseBody);

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            if (jsonResponse.has("error")) {
                JsonObject error = jsonResponse.getAsJsonObject("error");
                String message = error.has("data") ? error.get("data").getAsString() : error.get("message").getAsString();
                throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "Zabbix 接口调用失败");
            }

            return jsonResponse;
        }
    }
}
