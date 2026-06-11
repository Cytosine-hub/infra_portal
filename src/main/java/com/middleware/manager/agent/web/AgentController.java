package com.middleware.manager.agent.web;

import com.middleware.manager.agent.service.AgentService;
import com.middleware.manager.agent.service.AgentEvent;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.agent.tool.ToolContextHolder;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatMessageMapper;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import org.springframework.http.MediaType;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController("opsAgentController")
@RequestMapping("/api/ops-agent")
@Slf4j
public class AgentController {

    private final AgentService agentService;
    private final SkillLoader skillLoader;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AdminAccountMapper adminAccountMapper;
    private final PermissionService permissionService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AgentController(AgentService agentService, SkillLoader skillLoader,
                           ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper,
                           AdminAccountMapper adminAccountMapper,
                           PermissionService permissionService) {
        this.agentService = agentService;
        this.skillLoader = skillLoader;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.adminAccountMapper = adminAccountMapper;
        this.permissionService = permissionService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest req, Authentication authentication) {
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean clientOpen = new AtomicBoolean(true);

        sseExecutor.submit(() -> {
            try {
                String message = requireMessage(req.getMessage());
                Long actorId = resolveActorId(authentication);
                // 创建或获取会话
                Long sessionId = req.getSessionId();
                ChatSession session;
                if (sessionId != null) {
                    session = requireSessionForMode(sessionId, authentication, "ops");
                } else {
                    session = createNewSession(actorId);
                }

                // 保存用户消息
                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(session.getId());
                userMsg.setRole("user");
                userMsg.setContent(message);
                chatMessageMapper.insert(userMsg);

                // 调用 Agent（带重试回调）
                Map<String, Object> result;
                try {
                    ToolContextHolder.setAuthentication(authentication);
                    result = agentService.chat(message, req.getContext(), retryMsg -> {
                        safeSend(emitter, clientOpen, "retry", Map.of("message", retryMsg));
                    }, session.getId(), actorId, event -> sendAgentEvent(emitter, clientOpen, event));
                } finally {
                    ToolContextHolder.clear();
                }

                // 保存助手消息
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(session.getId());
                assistantMsg.setRole("assistant");
                assistantMsg.setContent((String) result.get("response"));
                chatMessageMapper.insert(assistantMsg);

                // 更新会话标题
                if (session.getTitle() == null || session.getTitle().isEmpty()) {
                    String title = message.length() > 30 ?
                            message.substring(0, 30) + "..." : message;
                    session.setTitle(title);
                    chatSessionMapper.update(session);
                }

                result.put("sessionId", session.getId());
                if (clientOpen.get()) {
                    safeSend(emitter, clientOpen, "result", result);
                    safeSend(emitter, clientOpen, "completed", Map.of("sessionId", session.getId()));
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

    private void sendAgentEvent(SseEmitter emitter, AtomicBoolean clientOpen, AgentEvent event) {
        safeSend(emitter, clientOpen, event.getType(), event.getPayload());
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
                log.warn("Failed to send agent SSE event type={}: {}", eventName, e.getMessage());
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
                || lower.contains("async request");
    }

    private ChatSession createNewSession(Long createdBy) {
        ChatSession session = new ChatSession();
        session.setTitle("");
        session.setMode("ops");
        session.setCreatedBy(createdBy);
        chatSessionMapper.insert(session);
        return session;
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions(Authentication authentication) {
        if (canViewAllSessions(authentication)) {
            return chatSessionMapper.findAllByOrderByUpdatedAtDesc();
        }
        return chatSessionMapper.findByCreatedByOrderByUpdatedAtDesc(resolveActorId(authentication));
    }

    @GetMapping("/sessions/{id}")
    public List<ChatMessage> getSessionMessages(@PathVariable Long id, Authentication authentication) {
        requireAccessibleSession(id, authentication);
        return chatMessageMapper.findBySessionIdOrderByCreatedAtAsc(id);
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        return agentService.listTools();
    }

    @GetMapping("/skills")
    public List<Skill> listSkills() {
        return skillLoader.getAll();
    }

    @PostMapping("/skills")
    public Map<String, Object> saveSkill(@RequestBody Skill skill, Authentication authentication) {
        requireSkillAdmin(authentication);
        requireValidSkillName(skill);
        skillLoader.save(skill);
        return Map.of("status", "ok", "name", skill.getName());
    }

    @DeleteMapping("/skills/{name}")
    public Map<String, Object> deleteSkill(@PathVariable String name, Authentication authentication) {
        requireSkillAdmin(authentication);
        skillLoader.delete(name);
        return Map.of("status", "ok");
    }

    @PostMapping("/experience")
    public Map<String, Object> saveExperience(@RequestBody Skill skill, Authentication authentication) {
        requireSkillAdmin(authentication);
        requireValidSkillName(skill);
        skillLoader.save(skill);
        return Map.of("status", "ok", "name", skill.getName());
    }

    @PostMapping("/webhook/alert")
    public Map<String, Object> alertWebhook(@RequestBody AlertRequest alert) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("service", alert.getService());
        String message = "告警：" + alert.getDescription() + "，服务：" + alert.getService() + "，请排查";
        return agentService.chat(message, context);
    }

    public static class ChatRequest {
        private Long sessionId;
        private String message;
        private Map<String, String> context;
        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Map<String, String> getContext() { return context; }
        public void setContext(Map<String, String> context) { this.context = context; }
    }

    public static class AlertRequest {
        private String service;
        private String description;
        private String severity;
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
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
            throw new NotFoundException(ErrorCode.NOT_FOUND, ErrorMessages.NOT_FOUND);
        }
        return session;
    }

    private boolean canViewAllSessions(Authentication authentication) {
        return permissionService.isAdmin(authentication);
    }

    private void requireSkillAdmin(Authentication authentication) {
        if (!permissionService.isAdmin(authentication) && !permissionService.isCategoryAdmin(authentication)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
    }

    private void requireValidSkillName(Skill skill) {
        if (skill == null || skill.getName() == null || skill.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Skill 名称不能为空");
        }
        String name = skill.getName().trim();
        if (!name.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "Skill 名称只能包含字母、数字、下划线和中划线");
        }
        skill.setName(name);
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
        log.error("Ops agent SSE failed", e);
        return ErrorMessages.UNKNOWN_ERROR;
    }

    private boolean isRetryFailure(String message) {
        return ErrorMessages.LLM_RESPONSE_TIMEOUT.equals(message)
                || ErrorMessages.LLM_SERVICE_BUSY.equals(message);
    }
}
