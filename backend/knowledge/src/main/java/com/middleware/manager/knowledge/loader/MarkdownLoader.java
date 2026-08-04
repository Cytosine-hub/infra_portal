package com.middleware.manager.knowledge.loader;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MarkdownLoader implements DocumentLoader {

    @Override
    public String load(InputStream inputStream, String fileName) throws Exception {
        // Markdown 本身即结构化格式，原样读入，标题层级交给下游 TextSplitter 解析。
        // 不关闭输入流：流的生命周期归调用方（KnowledgeService / IngestTaskService）管理。
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }
}
