package com.middleware.manager.agent.web;

import com.middleware.manager.agent.service.AgentService;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatMessageMapper;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.knowledge.agent.DiagnosticAttachmentService;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.PermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentControllerMultipartTest {

    @Test
    @DisplayName("TC-DIAG-ATT-112 Ops multipart 请求应传递附件并保存元数据")
    void acceptsMultipartAttachments() {
        AgentService agentService = mock(AgentService.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        AdminAccountMapper accountMapper = mock(AdminAccountMapper.class);
        PermissionService permissionService = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);
        ChatSession session = new ChatSession();
        session.setId(2L);
        session.setMode("ops");
        when(permissionService.isAdmin(authentication)).thenReturn(true);
        when(sessionMapper.findById(2L)).thenReturn(session);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("response", "ok");
        response.put("toolsUsed", List.of());
        when(agentService.chat(eq("分析日志"), any(), any(), eq(2L), any(), any(), anyList()))
                .thenReturn(response);
        AgentController controller = new AgentController(agentService, mock(SkillLoader.class),
                sessionMapper, messageMapper, accountMapper, permissionService,
                new DiagnosticAttachmentService(List.of()));
        MockMultipartFile file = new MockMultipartFile(
                "attachments", "error.log", "text/plain", "timeout".getBytes());

        controller.chatMultipart(2L, "分析日志", List.of(file), authentication);

        verify(agentService, timeout(1000)).chat(eq("分析日志"), any(), any(),
                eq(2L), any(), any(), anyList());
        verify(messageMapper, timeout(1000)).insert(argThat((ChatMessage message) ->
                "user".equals(message.getRole())
                        && message.getAttachmentsText().contains("error.log")));
    }
}
