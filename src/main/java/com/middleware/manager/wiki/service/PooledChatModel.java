package com.middleware.manager.wiki.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 池化 ChatModel 包装器，控制对 LLM API 的并发调用数
 */
public class PooledChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(PooledChatModel.class);

    private final ChatModel delegate;
    private final Semaphore semaphore;
    private final int maxConcurrent;
    private final long timeoutSeconds;

    public PooledChatModel(ChatModel delegate, int maxConcurrent, long timeoutSeconds) {
        this.delegate = delegate;
        this.maxConcurrent = maxConcurrent;
        this.timeoutSeconds = timeoutSeconds;
        this.semaphore = new Semaphore(maxConcurrent);
        log.info("PooledChatModel initialized: maxConcurrent={}, timeout={}s", maxConcurrent, timeoutSeconds);
    }

    public PooledChatModel(ChatModel delegate, int maxConcurrent) {
        this(delegate, maxConcurrent, 300);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        try {
            log.debug("Waiting for LLM slot (available={}/{})", semaphore.availablePermits(), maxConcurrent);
            if (!semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "LLM 并发数已达上限，请稍后再试");
            }
            try {
                log.debug("Acquired LLM slot, calling API...");
                return delegate.chat(messages);
            } finally {
                semaphore.release();
                log.debug("Released LLM slot");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.middleware.manager.exception.BusinessException(com.middleware.manager.constant.ErrorCode.UNKNOWN_ERROR, "LLM 调用被中断");
        }
    }

    /**
     * 获取当前可用的并发槽位数
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 获取当前等待的请求数
     */
    public int queuedCount() {
        return semaphore.getQueueLength();
    }
}
