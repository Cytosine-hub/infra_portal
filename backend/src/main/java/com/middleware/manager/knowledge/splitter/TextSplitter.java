package com.middleware.manager.knowledge.splitter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextSplitter {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+.*$");

    private final int maxChunkSize;

    public TextSplitter() {
        this(DEFAULT_MAX_CHUNK_SIZE);
    }

    public TextSplitter(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public List<TextChunk> split(String text, String sourceTitle) {
        List<TextChunk> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        // Split by markdown headings
        List<String> sections = splitByHeadings(text);

        int globalIndex = 0;
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.length() <= maxChunkSize) {
                TextChunk chunk = new TextChunk();
                chunk.setContent(trimmed);
                chunk.setSourceTitle(sourceTitle);
                chunk.setChunkIndex(globalIndex++);
                result.add(chunk);
            } else {
                // Split large sections by newlines
                List<String> subChunks = splitByNewlines(trimmed);
                for (String sub : subChunks) {
                    String subTrimmed = sub.trim();
                    if (subTrimmed.isEmpty()) {
                        continue;
                    }
                    TextChunk chunk = new TextChunk();
                    chunk.setContent(subTrimmed);
                    chunk.setSourceTitle(sourceTitle);
                    chunk.setChunkIndex(globalIndex++);
                    result.add(chunk);
                }
            }
        }

        return result;
    }

    private List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                sections.add(text.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.start();
        }
        if (lastEnd < text.length()) {
            sections.add(text.substring(lastEnd));
        }

        return sections;
    }

    private List<String> splitByNewlines(String text) {
        List<String> parts = new ArrayList<>();
        String[] lines = text.split("\\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            // 单行超长时强制按字符切分
            if (line.length() > maxChunkSize) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < line.length(); i += maxChunkSize) {
                    parts.add(line.substring(i, Math.min(i + maxChunkSize, line.length())));
                }
                continue;
            }
            if (current.length() + line.length() + 1 > maxChunkSize && current.length() > 0) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(line);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    public static class TextChunk {
        private String content;
        private String sourceTitle;
        private int chunkIndex;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
    }
}
