package com.middleware.manager.knowledge.repository;

import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeChunkMapper {

    KnowledgeChunk findById(@Param("id") Long id);

    List<KnowledgeChunk> findAll();

    List<KnowledgeChunk> findBySourceTitleContaining(@Param("keyword") String keyword);

    /**
     * 带权重的关键词搜索。
     * @param terms 搜索词列表（第一个是完整关键词，后面是双字和单字）
     * @param weights 对应权重列表（100, 10, 1）
     * @param limit 返回数量限制
     */
    List<KnowledgeChunk> findByTermsWithScore(@Param("terms") List<String> terms,
                                               @Param("weights") List<Integer> weights,
                                               @Param("limit") int limit);

    List<KnowledgeChunk> findBySourceTitleAndSourceType(@Param("sourceTitle") String sourceTitle, @Param("sourceType") String sourceType);

    List<Map<String, Object>> findDistinctSources();

    long count();

    int insert(KnowledgeChunk chunk);

    int deleteBySourceIdAndSourceType(@Param("sourceId") Long sourceId, @Param("sourceType") String sourceType);

    int deleteBySourceTitleAndSourceType(@Param("sourceTitle") String sourceTitle, @Param("sourceType") String sourceType);

    int deleteBySourceTitleLike(@Param("pattern") String pattern);

    int deleteAll();
}
