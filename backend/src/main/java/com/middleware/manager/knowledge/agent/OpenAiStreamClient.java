package com.middleware.manager.knowledge.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OpenAiStreamClient {
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient okHttpClient;
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public OpenAiStreamClient(OkHttpClient okHttpClient,
                              @Value("${langchain4j.open-ai.chat-model.base-url:${app.ai.base-url}}") String baseUrl,
                              @Value("${langchain4j.open-ai.chat-model.api-key:${app.ai.api-key}}") String apiKey,
                              @Value("${langchain4j.open-ai.chat-model.model-name:${app.ai.model}}") String model,
                              @Value("${langchain4j.open-ai.chat-model.max-tokens:${app.ai.max-tokens:4096}}") int maxTokens,
                              @Value("${langchain4j.open-ai.chat-model.temperature:${app.ai.temperature:0.1}}") double temperature) {
        this.okHttpClient = okHttpClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public String stream(List<ChatMessage> messages, Consumer<String> onDelta) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", temperature);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("stream", true);
        body.add("messages", toOpenAiMessages(messages));

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("stream chat failed status=" + response.code());
            }
            String contentType = response.header("Content-Type", "");
            if (!contentType.toLowerCase().contains("text/event-stream")) {
                throw new IOException("stream chat returned non-sse content-type=" + contentType);
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("stream chat returned empty body");
            }
            return readSse(responseBody, onDelta);
        }
    }

    private String readSse(ResponseBody responseBody, Consumer<String> onDelta) throws IOException {
        StringBuilder answer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                String delta = extractDelta(data);
                if (delta != null && !delta.isEmpty()) {
                    answer.append(delta);
                    onDelta.accept(delta);
                }
            }
        }
        return answer.toString();
    }

    private String extractDelta(String data) {
        try {
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = choice.getAsJsonObject("delta");
            if (delta == null) {
                return "";
            }
            JsonElement content = delta.get("content");
            return content == null || content.isJsonNull() ? "" : content.getAsString();
        } catch (RuntimeException e) {
            log.debug("Skip malformed stream chunk");
            return "";
        }
    }

    private JsonArray toOpenAiMessages(List<ChatMessage> messages) {
        JsonArray array = new JsonArray();
        for (ChatMessage message : messages) {
            JsonObject item = new JsonObject();
            if (message instanceof SystemMessage systemMessage) {
                item.addProperty("role", "system");
                item.addProperty("content", systemMessage.text());
            } else if (message instanceof UserMessage userMessage) {
                item.addProperty("role", "user");
                item.addProperty("content", userMessage.hasSingleText() ? userMessage.singleText() : userMessage.toString());
            } else if (message instanceof AiMessage aiMessage) {
                item.addProperty("role", "assistant");
                item.addProperty("content", aiMessage.text() != null ? aiMessage.text() : "");
            } else {
                item.addProperty("role", message.type().name().toLowerCase());
                item.addProperty("content", message.toString());
            }
            array.add(item);
        }
        return array;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
