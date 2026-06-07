package com.middleware.manager.knowledge.web;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.knowledge.agent.TroubleshootAgent;
import com.middleware.manager.knowledge.agent.TroubleshootAgent.AgentResponse;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    @Autowired
    private TroubleshootAgent agent;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private AdminAccountMapper adminAccountMapper;

    @Autowired
    private PermissionService permissionService;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request, Authentication authentication) {
        SseEmitter emitter = new SseEmitter(300_000L);

        sseExecutor.submit(() -> {
            try {
                String message = requireMessage(request.getMessage());
                Long sessionId = request.getSessionId();
                if (sessionId == null) {
                    ChatSession session = agent.createSession(resolveActorId(authentication));
                    sessionId = session.getId();
                } else {
                    requireSessionForMode(sessionId, authentication, "rag");
                }

                Long finalSessionId = sessionId;
                AgentResponse response = agent.chat(sessionId, message, retryMsg -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("retry")
                                .data(Map.of("message", retryMsg)));
                    } catch (IOException ignored) {}
                }, authentication);

                Map<String, Object> result = new HashMap<>();
                result.put("answer", response.getAnswer());
                result.put("references", response.getReferences());
                result.put("sessionId", finalSessionId);
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.complete();
            } catch (Exception e) {
                String msg = toClientError(e);
                boolean isRetryFail = isRetryFailure(msg);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("error", msg, "retryFailed", isRetryFail)));
                } catch (IOException ignored) {}
                emitter.complete();
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> emitter.complete());
        return emitter;
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions(Authentication authentication) {
        if (canViewAllSessions(authentication)) {
            return agent.getAllSessions();
        }
        return chatSessionMapper.findByCreatedByOrderByUpdatedAtDesc(resolveActorId(authentication));
    }

    @GetMapping("/sessions/{id}")
    public List<ChatMessage> getSessionMessages(@PathVariable Long id, Authentication authentication) {
        requireAccessibleSession(id, authentication);
        return agent.getSessionMessages(id);
    }

    @PostMapping("/sessions")
    public ChatSession createSession(@RequestBody(required = false) Map<String, String> body,
                                     Authentication authentication) {
        ChatSession session = new ChatSession();
        session.setTitle("");
        String mode = (body != null && body.get("mode") != null) ? body.get("mode") : "rag";
        if (!"rag".equals(mode) && !"ops".equals(mode)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "mode must be 'rag' or 'ops'");
        }
        session.setMode(mode);
        session.setCreatedBy(resolveActorId(authentication));
        chatSessionMapper.insert(session);
        return session;
    }

    @PatchMapping("/sessions/{id}/mode")
    public Object updateSessionMode(@PathVariable Long id, @RequestBody Map<String, String> body,
                                    Authentication authentication) {
        String mode = body.get("mode");
        if (mode == null || (!mode.equals("rag") && !mode.equals("ops"))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "mode must be 'rag' or 'ops'");
        }
        ChatSession session = requireAccessibleSession(id, authentication);
        session.setMode(mode);
        chatSessionMapper.update(session);
        return session;
    }

    public static class ChatRequest {
        private Long sessionId;
        private String message;

        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "消息不能为空");
        }
        return message.trim();
    }

    private ChatSession requireSessionForMode(Long sessionId, Authentication authentication, String mode) {
        ChatSession session = requireAccessibleSession(sessionId, authentication);
        if (!mode.equals(session.getMode())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "会话模式不匹配");
        }
        return session;
    }

    private ChatSession requireAccessibleSession(Long sessionId, Authentication authentication) {
        ChatSession session = canViewAllSessions(authentication)
                ? chatSessionMapper.findById(sessionId)
                : chatSessionMapper.findByIdAndCreatedBy(sessionId, resolveActorId(authentication));
        if (session == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private boolean canViewAllSessions(Authentication authentication) {
        return permissionService.isAdmin(authentication);
    }

    private Long resolveActorId(Authentication authentication) {
        if (authentication == null) return 0L;
        try {
            AdminAccount account = adminAccountMapper.findByUsername(authentication.getName());
            return account != null ? account.getId() : 0L;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.UNKNOWN_ERROR);
        }
    }

    private String toClientError(Exception e) {
        if (e instanceof BusinessException) {
            return e.getMessage() != null ? e.getMessage() : ErrorMessages.UNKNOWN_ERROR;
        }
        log.error("Agent SSE failed", e);
        return ErrorMessages.UNKNOWN_ERROR;
    }

    private boolean isRetryFailure(String message) {
        return ErrorMessages.LLM_RESPONSE_TIMEOUT.equals(message)
                || ErrorMessages.LLM_SERVICE_BUSY.equals(message);
    }
}
