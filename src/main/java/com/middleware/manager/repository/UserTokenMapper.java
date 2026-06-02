package com.middleware.manager.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserTokenMapper {

    int insert(@Param("token") String token, @Param("username") String username, @Param("expiresAt") LocalDateTime expiresAt);

    String findUsernameByToken(@Param("token") String token);

    int updateExpiry(@Param("token") String token, @Param("expiresAt") LocalDateTime expiresAt);

    int deleteByToken(@Param("token") String token);

    int deleteByUsername(@Param("username") String username);

    int deleteExpired();
}
