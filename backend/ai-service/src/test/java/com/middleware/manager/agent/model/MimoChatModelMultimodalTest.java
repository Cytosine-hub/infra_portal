package com.middleware.manager.agent.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MimoChatModelMultimodalTest {

    @Test
    @DisplayName("TC-DIAG-ATT-109 Ops Agent 图片应按 image_url 多模态格式发送")
    void serializesImagePayload() {
        ChatModel.ImagePayload image = new ChatModel.ImagePayload("image/png", "aGVsbG8=");
        ChatModel.Message message = ChatModel.Message.user("分析截图", List.of(image));

        JsonArray result = MimoChatModel.toJsonMessages(List.of(message));

        JsonObject user = result.get(0).getAsJsonObject();
        JsonArray content = user.getAsJsonArray("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(1).getAsJsonObject().get("type").getAsString()).isEqualTo("image_url");
        assertThat(content.get(1).getAsJsonObject()
                .getAsJsonObject("image_url").get("url").getAsString())
                .isEqualTo("data:image/png;base64,aGVsbG8=");
    }
}
