package com.middleware.manager.repository;

import com.middleware.manager.domain.KnowledgeImportSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeImportSourceMapper {
    KnowledgeImportSource findPublishedStandardDocument(@Param("id") Long id);

    KnowledgeImportSource findPublishedForumPost(@Param("id") Long id);
}
