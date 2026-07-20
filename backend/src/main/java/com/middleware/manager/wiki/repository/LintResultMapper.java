package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.LintResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface LintResultMapper {
    List<LintResult> findAll();
    List<LintResult> findUnresolved();
    List<LintResult> findBySeverity(@Param("severity") String severity);
    List<LintResult> findByPageId(@Param("pageId") Long pageId);
    int insert(LintResult result);
    int upsert(LintResult result);
    int resolve(@Param("id") Long id, @Param("resolvedBy") Long resolvedBy);
    int deleteResolved();
    int countUnresolved();
}
