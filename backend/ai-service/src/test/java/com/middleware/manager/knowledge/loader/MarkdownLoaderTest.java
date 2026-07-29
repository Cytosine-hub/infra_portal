package com.middleware.manager.knowledge.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownLoaderTest {

    private final MarkdownLoader loader = new MarkdownLoader();

    @Test
    @DisplayName("TC-LOADER-009 应完整读取 Markdown 内容并保留标题层级")
    void readsFullMarkdownContent() throws Exception {
        String markdown = "# 一级标题\n\n## 二级标题\n\n正文内容，含中文。\n";

        String content = loader.load(
                new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)), "doc.md");

        assertThat(content).isEqualTo(markdown);
    }

    @Test
    @DisplayName("TC-LOADER-010 空文件应返回空字符串而不是抛异常")
    void emptyFileReturnsEmptyString() throws Exception {
        String content = loader.load(new ByteArrayInputStream(new byte[0]), "empty.md");

        assertThat(content).isEmpty();
    }

    @Test
    @DisplayName("TC-LOADER-011 读取后不应关闭调用方传入的输入流")
    void doesNotCloseCallerOwnedStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream stream = new ByteArrayInputStream("# 标题".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };

        loader.load(stream, "doc.md");

        assertThat(closed).isFalse();
    }
}
