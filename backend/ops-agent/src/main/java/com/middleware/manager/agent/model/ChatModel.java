package com.middleware.manager.agent.model;

import java.util.List;
import java.util.function.Consumer;

public interface ChatModel {
    String generate(List<Message> messages);

    default String generate(List<Message> messages, Consumer<String> onRetry) {
        return generate(messages);
    }

    record Message(String role, String content, List<ImagePayload> images) {
        public Message(String role, String content) {
            this(role, content, List.of());
        }

        public Message {
            images = images == null ? List.of() : List.copyOf(images);
        }

        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message user(String content, List<ImagePayload> images) {
            return new Message("user", content, images);
        }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }

    record ImagePayload(String contentType, String base64Data) {}
}
