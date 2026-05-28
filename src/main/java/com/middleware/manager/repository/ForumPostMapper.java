package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumPostMapper {

    List<ForumPost> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Fulltext search with optional tag filter.
     * Uses MATCH(...) AGAINST(...) IN BOOLEAN MODE for keyword search.
     */
    List<ForumPost> search(@Param("keyword") String keyword, @Param("tag") String tag);

    ForumPost findById(Long id);

    int insert(ForumPost post);

    int update(ForumPost post);

    int deleteById(Long id);

    int incrementViewCount(Long id);

    int incrementLikeCount(Long id);

    int decrementLikeCount(Long id);

    int updateCommentCount(Long id);
}
