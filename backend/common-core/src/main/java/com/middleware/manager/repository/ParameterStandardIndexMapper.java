package com.middleware.manager.repository;

import com.middleware.manager.domain.ParameterStandard;

import java.util.List;

/**
 * 参数标准的只读访问，供 ai-service 做索引对账。
 * <p>standards 的写入归 core-service 的 ParameterStandardService 独占，这里只读不写。
 */
public interface ParameterStandardIndexMapper {

    List<ParameterStandard> findPublished();
}
