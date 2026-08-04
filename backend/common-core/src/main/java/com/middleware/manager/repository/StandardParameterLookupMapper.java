package com.middleware.manager.repository;

import com.middleware.manager.knowledge.service.ParameterAnswerRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 参数标准的只读联表查询，供参数精确查询接口使用。
 * <p>写入归 core-service 的 StandardParameterService 独占，这里只读。
 */
public interface StandardParameterLookupMapper {

    List<ParameterAnswerRow> search(@Param("software") String software,
                                    @Param("name") String name,
                                    @Param("limit") Integer limit);
}
