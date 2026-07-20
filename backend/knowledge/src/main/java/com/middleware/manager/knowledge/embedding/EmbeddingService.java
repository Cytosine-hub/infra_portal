package com.middleware.manager.knowledge.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EmbeddingService implements EmbeddingProvider {
    private final EmbeddingModel embeddingModel;

    @Value("${app.embedding.max-chars:1500}")
    private int maxChars;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        if (text.length() > maxChars) {
            log.debug("Truncating text from {} to {} chars", text.length(), maxChars);
            text = text.substring(0, maxChars);
        }
        log.debug("Embedding text({}): {}", text.length(), text.length() > 50 ? text.substring(0, 50) + "..." : text);
        Embedding result = embeddingModel.embed(text).content();
        return result.vector();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return new ArrayList<>();

        int parallel = Math.min(texts.size(), 8);
        ExecutorService executor = Executors.newFixedThreadPool(parallel);
        try {
            List<CompletableFuture<float[]>> futures = new ArrayList<>();
            for (String text : texts) {
                futures.add(CompletableFuture.supplyAsync(() -> embed(text), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<float[]> results = new ArrayList<>();
            for (CompletableFuture<float[]> f : futures) {
                results.add(f.join());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
