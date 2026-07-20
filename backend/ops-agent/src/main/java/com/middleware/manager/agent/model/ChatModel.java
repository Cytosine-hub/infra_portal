package com.middleware.manager.agent.model;

import java.util.List;
import java.util.function.Consumer;

public interface ChatModel {
    String generate(List<Message> messages);

    default String generate(List<Message> messages, Consumer<String> onRetry) {
        return generate(messages);
    }

    record Message(String role, String content) {
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }
}
