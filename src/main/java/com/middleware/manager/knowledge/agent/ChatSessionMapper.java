package com.middleware.manager.knowledge.agent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    ChatSession findById(@Param("id") Long id);

    List<ChatSession> findAllByOrderByUpdatedAtDesc();

    int insert(ChatSession session);

    int update(ChatSession session);
}
