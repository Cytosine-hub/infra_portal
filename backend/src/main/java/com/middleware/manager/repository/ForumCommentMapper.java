package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumComment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ForumCommentMapper {

    List<ForumComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    ForumComment findById(Long id);

    int insert(ForumComment comment);

    int deleteById(Long id);

    int deleteByPostId(Long postId);

    int incrementLikeCount(Long id);
}
