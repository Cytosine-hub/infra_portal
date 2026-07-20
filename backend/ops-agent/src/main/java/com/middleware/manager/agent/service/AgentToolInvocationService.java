package com.middleware.manager.agent.service;

import com.middleware.manager.agent.domain.AgentToolInvocation;
import com.middleware.manager.agent.repository.AgentToolInvocationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentToolInvocationService {
    private final AgentToolInvocationMapper invocationMapper;

    public AgentToolInvocationService(AgentToolInvocationMapper invocationMapper) {
        this.invocationMapper = invocationMapper;
    }

    @Transactional
    public void record(AgentToolInvocation invocation) {
        invocationMapper.insert(invocation);
    }
}
