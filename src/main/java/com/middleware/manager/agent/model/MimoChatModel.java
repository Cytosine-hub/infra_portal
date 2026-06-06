package com.middleware.manager.agent.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class MimoChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(MimoChatModel.class);
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    public MimoChatModel(@Value("${app.ai.api-key}") String apiKey,
                         @Value("${app.ai.base-url}") String baseUrl,
                         @Value("${app.ai.model}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    private static final int MAX_RETRIES = 5;

    @Override
    public String generate(List<Message> messages) {
        return generate(messages, null);
    }

    @Override
    public String generate(List<Message> messages, Consumer<String> onRetry) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.1);
        body.addProperty("max_tokens", 4096);

        JsonArray msgs = new JsonArray();
        for (Message msg : messages) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.role());
            m.addProperty("content", msg.content());
            msgs.add(m);
        }
        body.add("messages", msgs);

        okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                gson.toJson(body), okhttp3.MediaType.parse("application/json"));

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (okhttp3.Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("[Agent] LLM API error (attempt {}/{}): {} {}", attempt, MAX_RETRIES, response.code(), respBody);
                    lastException = new RuntimeException("LLM API error: " + response.code());
                } else {
                    JsonObject json = gson.fromJson(respBody, JsonObject.class);
                    return json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            } catch (IOException e) {
                log.error("[Agent] LLM API call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                lastException = new RuntimeException("LLM API call failed", e);
            }
            if (attempt < MAX_RETRIES) {
                if (onRetry != null) {
                    onRetry.accept("模型响应超时，正在重试（" + attempt + "/" + MAX_RETRIES + "）...");
                }
                try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "模型响应超时，请稍后再试");
    }
}
