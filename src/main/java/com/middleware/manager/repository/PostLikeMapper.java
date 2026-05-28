package com.middleware.manager.repository;

import com.middleware.manager.domain.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper {

    boolean existsByPostIdAndUsername(@Param("postId") Long postId, @Param("username") String username);

    int deleteByPostIdAndUsername(@Param("postId") Long postId, @Param("username") String username);

    int insert(PostLike like);
}
