package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.repository.IngestTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编译进度更新助手。
 * <p>
 * 每个方法使用 {@code REQUIRES_NEW} 传播级别，确保进度/中间产物更新
 * 在独立事务中提交，不受外层 {@code @Transactional} 影响。
 * 这样前端轮询进度时能实时看到更新，而不是等到整个编译事务提交。
 */
@Component
@Slf4j
public class IngestProgressHelper {

    private final IngestTaskMapper taskMapper;

    public IngestProgressHelper(IngestTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long taskId, int progress, String step, int completedUnits) {
        taskMapper.updateProgress(taskId, progress, step, completedUnits);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressWithTotal(Long taskId, int progress, String step,
                                        int completedUnits, int totalUnits) {
        taskMapper.updateProgressWithTotal(taskId, progress, step, completedUnits, totalUnits);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSectionFacts(Long taskId, String sectionFactsJson) {
        taskMapper.updateSectionFacts(taskId, sectionFactsJson);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePagePlan(Long taskId, String pagePlanJson) {
        taskMapper.updatePagePlan(taskId, pagePlanJson);
    }
}
