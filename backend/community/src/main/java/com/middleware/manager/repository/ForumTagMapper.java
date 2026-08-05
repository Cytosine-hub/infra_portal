package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumTagMapper {

    List<ForumTag> findAllByOrderByPostCountDesc();

    ForumTag findByNameIgnoreCase(String name);

    ForumTag findByNameIgnoreCaseAndCategory(@Param("name") String name, @Param("category") String category);

    List<ForumTag> findByAuthorUsername(String username);

    List<ForumTag> findByCategory(String category);

    boolean isUsedByAuthor(@Param("id") Long id, @Param("username") String username);

    boolean hasAssociationsOutsideAuthor(@Param("id") Long id, @Param("username") String username);

    ForumTag findById(Long id);

    int insert(ForumTag tag);

    int update(ForumTag tag);

    int incrementPostCount(Long id);

    int decrementPostCount(Long id);

    List<ForumTag> findByPostId(Long postId);

    List<ForumTag> findByPostIds(@Param("postIds") List<Long> postIds);

    int insertPostTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    int deletePostTag(@Param("postId") Long postId, @Param("tagId") Long tagId);

    int deletePostTagsByPostId(Long postId);

    int reassignAuthorTag(@Param("newTagId") Long newTagId, @Param("oldTagId") Long oldTagId,
                          @Param("username") String username);

    int deleteAuthorTagAssociations(@Param("tagId") Long tagId, @Param("username") String username);

    int deletePostTagsByTagId(Long tagId);

    int refreshPostCount(Long id);

    int deleteById(Long id);
}
