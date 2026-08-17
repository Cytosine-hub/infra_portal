package com.middleware.manager.knowledge.web;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.knowledge.agent.DiagnosticAttachment;
import com.middleware.manager.knowledge.agent.DiagnosticAttachmentService;
import com.middleware.manager.knowledge.agent.TroubleshootAgent;
import com.middleware.manager.knowledge.agent.TroubleshootAgent.AgentResponse;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.PermissionService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    private static final long SSE_TIMEOUT_DISABLED = 0L;

    private final TroubleshootAgent agent;
    private final ChatSessionMapper chatSessionMapper;
    private final AdminAccountMapper adminAccountMapper;
    private final PermissionService permissionService;
    private final DiagnosticAttachmentService attachmentService;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AgentController(TroubleshootAgent agent,
                           ChatSessionMapper chatSessionMapper,
                           AdminAccountMapper adminAccountMapper,
                           PermissionService permissionService,
                           DiagnosticAttachmentService attachmentService) {
        this.agent = agent;
        this.chatSessionMapper = chatSessionMapper;
        this.adminAccountMapper = adminAccountMapper;
        this.permissionService = permissionService;
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request, Authentication authentication) {
        String message = requireMessage(request.getMessage(), false);
        return startChat(request.getSessionId(), message, List.of(), authentication);
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatMultipart(@RequestParam(required = false) Long sessionId,
                                    @RequestParam(required = false) String message,
                                    @RequestPart(name = "attachments", required = false)
                                    List<MultipartFile> files,
                                    Authentication authentication) {
        List<DiagnosticAttachment> attachments = attachmentService.prepare(files);
        return startChat(sessionId, requireMessage(message, !attachments.isEmpty()),
                attachments, authentication);
    }

    private SseEmitter startChat(Long requestedSessionId, String message,
                                 List<DiagnosticAttachment> attachments,
                                 Authentication authentication) {
        // 上游与代理的空闲超时会清理停滞请求，固定总时长反而会截断仍在正常输出的长回答。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_DISABLED);
        AtomicBoolean clientOpen = new AtomicBoolean(true);

        sseExecutor.submit(() -> {
            try {
                Long sessionId = requestedSessionId;
                if (sessionId == null) {
                    ChatSession session = agent.createSession(resolveActorId(authentication));
                    sessionId = session.getId();
                } else {
                    requireSessionForMode(sessionId, authentication, "rag");
                }

                Long finalSessionId = sessionId;
                AgentResponse response = agent.chatStream(sessionId, message, attachments, retryMsg -> {
                    safeSend(emitter, clientOpen, "retry", Map.of("message", retryMsg));
                }, delta -> {
                    if (!safeSend(emitter, clientOpen, "delta", Map.of("content", delta))) {
                        throw new IllegalStateException("client disconnected");
                    }
                }, authentication);

                Map<String, Object> result = new HashMap<>();
                result.put("answer", response.getAnswer());
                result.put("references", response.getReferences());
                result.put("sessionId", finalSessionId);
                if (clientOpen.get()) {
                    safeSend(emitter, clientOpen, "result", result);
                    safeSend(emitter, clientOpen, "completed", Map.of("sessionId", finalSessionId));
                }
                completeQuietly(emitter);
            } catch (Exception e) {
                if (!clientOpen.get() || isClientDisconnect(e)) {
                    completeQuietly(emitter);
                    return;
                }
                String msg = toClientError(e);
                boolean isRetryFail = isRetryFailure(msg);
                safeSend(emitter, clientOpen, "error", Map.of("error", msg, "retryFailed", isRetryFail));
                completeQuietly(emitter);
            }
        });

        emitter.onCompletion(() -> clientOpen.set(false));
        emitter.onTimeout(() -> {
            clientOpen.set(false);
            completeQuietly(emitter);
        });
        emitter.onError(t -> {
            clientOpen.set(false);
            completeQuietly(emitter);
        });
        return emitter;
    }

    private boolean safeSend(SseEmitter emitter, AtomicBoolean clientOpen, String eventName, Object data) {
        if (!clientOpen.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException e) {
            clientOpen.set(false);
            if (!isClientDisconnect(e)) {
                log.warn("Failed to send diagnostics SSE event type={}: {}", eventName, e.getMessage());
            }
            return false;
        } catch (IllegalStateException e) {
            clientOpen.set(false);
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("SSE emitter already completed");
        }
    }

    private boolean isClientDisconnect(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("broken pipe")
                || lower.contains("connection reset")
                || lower.contains("response body has already been written")
                || lower.contains("async request")
                || lower.contains("client disconnected");
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

    private String requireMessage(String message, boolean hasAttachments) {
        if (message == null || message.isBlank()) {
            if (hasAttachments) {
                return ErrorMessages.DIAGNOSTIC_ATTACHMENT_PROMPT;
            }
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.DIAGNOSTIC_MESSAGE_REQUIRED);
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
