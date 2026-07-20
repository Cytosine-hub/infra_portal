package com.middleware.manager.knowledge.agent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    ChatSession findById(@Param("id") Long id);

    ChatSession findByIdAndCreatedBy(@Param("id") Long id, @Param("createdBy") Long createdBy);

    List<ChatSession> findAllByOrderByUpdatedAtDesc();

    List<ChatSession> findByCreatedByOrderByUpdatedAtDesc(@Param("createdBy") Long createdBy);

    int insert(ChatSession session);

    int update(ChatSession session);
}
