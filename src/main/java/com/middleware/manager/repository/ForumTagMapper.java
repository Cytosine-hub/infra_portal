package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumTagMapper {

    List<ForumTag> findAllByOrderByPostCountDesc();

    ForumTag findByNameIgnoreCase(String name);

    ForumTag findById(Long id);

    int insert(ForumTag tag);

    int update(ForumTag tag);

    int incrementPostCount(Long id);

    int decrementPostCount(Long id);

    List<ForumTag> findByPostId(Long postId);

    int insertPostTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    int deletePostTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    int deletePostTagsByPostId(Long postId);
}
