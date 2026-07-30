package com.middleware.manager.knowledge.repository;

import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.store.VectorSearchFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeChunkMapper {

    KnowledgeChunk findById(@Param("id") Long id);

    List<KnowledgeChunk> findAll();

    List<KnowledgeChunk> findBySourceTitleContaining(@Param("keyword") String keyword);

    List<KnowledgeChunk> findBySourceTitleAndSourceType(@Param("sourceTitle") String sourceTitle, @Param("sourceType") String sourceType);

    List<Map<String, Object>> findDistinctSources();

    long count();

    int insert(KnowledgeChunk chunk);

    int deleteBySourceIdAndSourceType(@Param("sourceId") Long sourceId, @Param("sourceType") String sourceType);

    int deleteBySourceTitleAndSourceType(@Param("sourceTitle") String sourceTitle, @Param("sourceType") String sourceType);

    int deleteBySourceTitleLike(@Param("pattern") String pattern);

    int deleteAll();
}
