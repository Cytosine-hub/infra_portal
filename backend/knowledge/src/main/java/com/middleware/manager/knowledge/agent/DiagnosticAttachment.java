package com.middleware.manager.knowledge.agent;

import dev.langchain4j.data.message.ImageContent;

public record DiagnosticAttachment(
        String name,
        String contentType,
        long size,
        Kind kind,
        String extractedText,
        String base64Data) {

    public enum Kind {
        IMAGE,
        DOCUMENT
    }

    public Metadata metadata() {
        return new Metadata(name, contentType, size, kind.name().toLowerCase());
    }

    public ImageContent toImageContent() {
        if (kind != Kind.IMAGE) {
            throw new IllegalStateException("Only image attachments can be converted to ImageContent");
        }
        return ImageContent.from(base64Data, contentType);
    }

    public record Metadata(String name, String contentType, long size, String kind) {}
}
