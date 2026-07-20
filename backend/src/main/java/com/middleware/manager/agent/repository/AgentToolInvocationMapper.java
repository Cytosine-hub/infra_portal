package com.middleware.manager.agent.repository;

import com.middleware.manager.agent.domain.AgentToolInvocation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentToolInvocationMapper {
    int insert(AgentToolInvocation invocation);
}
