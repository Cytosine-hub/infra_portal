package com.middleware.manager.knowledge.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStreamClientMultimodalTest {

    @Test
    @DisplayName("TC-DIAG-ATT-107 图片应按 OpenAI image_url 多模态格式发送")
    void serializesImageContentAsDataUrl() {
        OpenAiStreamClient client = new OpenAiStreamClient(
                new OkHttpClient(), "http://localhost/v1", "key", "vision-model", 1024, 0.1);
        UserMessage message = UserMessage.from(List.of(
                TextContent.from("分析截图"),
                ImageContent.from("aGVsbG8=", "image/png")
        ));

        JsonArray result = client.toOpenAiMessages(List.of(message));

        JsonObject user = result.get(0).getAsJsonObject();
        JsonArray content = user.getAsJsonArray("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0).getAsJsonObject().get("type").getAsString()).isEqualTo("text");
        assertThat(content.get(1).getAsJsonObject().get("type").getAsString()).isEqualTo("image_url");
        assertThat(content.get(1).getAsJsonObject()
                .getAsJsonObject("image_url").get("url").getAsString())
                .isEqualTo("data:image/png;base64,aGVsbG8=");
    }
}
