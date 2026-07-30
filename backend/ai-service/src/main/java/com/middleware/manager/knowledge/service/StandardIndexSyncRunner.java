package com.middleware.manager.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把已发布的参数标准对账进索引。
 * <p>异步执行且吞掉异常：Milvus 或 embedding 暂时不可用时不应阻塞服务启动，
 * 下次启动或手动调用 /api/knowledge/sync-standards 会重试。
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.startup.runners-enabled", matchIfMissing = true)
@Slf4j
public class StandardIndexSyncRunner implements ApplicationRunner {

    private final StandardIndexSyncService syncService;

    public StandardIndexSyncRunner(StandardIndexSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(() -> {
            try {
                syncService.sync();
            } catch (Exception e) {
                log.warn("启动期标准索引对账失败，可稍后手动触发 /api/knowledge/sync-standards: {}",
                        e.getMessage());
            }
        }, "standard-index-sync");
        worker.setDaemon(true);
        worker.start();
    }
}
