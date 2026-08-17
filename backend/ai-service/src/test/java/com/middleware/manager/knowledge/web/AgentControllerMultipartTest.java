package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.knowledge.agent.DiagnosticAttachmentService;
import com.middleware.manager.knowledge.agent.TroubleshootAgent;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.PermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerMultipartTest {

    @Test
    @DisplayName("TC-DIAG-ATT-111 RAG multipart 请求应解析附件并支持仅附件提问")
    void acceptsMultipartAttachments() {
        TroubleshootAgent agent = mock(TroubleshootAgent.class);
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        AdminAccountMapper accountMapper = mock(AdminAccountMapper.class);
        PermissionService permissionService = mock(PermissionService.class);
        Authentication authentication = mock(Authentication.class);
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setMode("rag");
        when(permissionService.isAdmin(authentication)).thenReturn(true);
        when(sessionMapper.findById(1L)).thenReturn(session);
        when(agent.chatStream(eq(1L), eq("请分析附件内容并给出排查结论"), anyList(),
                any(), any(), eq(authentication)))
                .thenReturn(new TroubleshootAgent.AgentResponse("ok", List.of()));
        AgentController controller = new AgentController(agent, sessionMapper, accountMapper,
                permissionService, new DiagnosticAttachmentService(List.of()));
        MockMultipartFile file = new MockMultipartFile(
                "attachments", "error.log", "text/plain", "timeout".getBytes());

        SseEmitter emitter = controller.chatMultipart(1L, "", List.of(file), authentication);

        assertThat(emitter.getTimeout()).isZero();
        verify(agent, timeout(1000)).chatStream(eq(1L),
                eq("请分析附件内容并给出排查结论"),
                anyList(), any(), any(), eq(authentication));
    }
}
