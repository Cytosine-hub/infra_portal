package com.middleware.manager.knowledge.loader;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Component
public class MarkdownLoader implements DocumentLoader {

    @Override
    public String load(InputStream inputStream, String fileName) throws Exception {
        Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name());
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }
}
