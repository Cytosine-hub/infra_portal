package com.middleware.manager.agent.web;

import com.middleware.manager.agent.service.AgentService;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatMessageMapper;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController("opsAgentController")
@RequestMapping("/api/ops-agent")
public class AgentController {

    private final AgentService agentService;
    private final SkillLoader skillLoader;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AgentController(AgentService agentService, SkillLoader skillLoader,
                           ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper) {
        this.agentService = agentService;
        this.skillLoader = skillLoader;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(300_000L);

        sseExecutor.submit(() -> {
            try {
                // 创建或获取会话
                Long sessionId = req.getSessionId();
                ChatSession session;
                if (sessionId != null) {
                    session = chatSessionMapper.findById(sessionId);
                    if (session == null) session = createNewSession();
                } else {
                    session = createNewSession();
                }

                // 保存用户消息
                ChatMessage userMsg = new ChatMessage();
                userMsg.setSessionId(session.getId());
                userMsg.setRole("user");
                userMsg.setContent(req.getMessage());
                chatMessageMapper.insert(userMsg);

                // 调用 Agent（带重试回调）
                Map<String, Object> result = agentService.chat(req.getMessage(), req.getContext(), retryMsg -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("retry")
                                .data(Map.of("message", retryMsg)));
                    } catch (IOException ignored) {}
                });

                // 保存助手消息
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setSessionId(session.getId());
                assistantMsg.setRole("assistant");
                assistantMsg.setContent((String) result.get("response"));
                chatMessageMapper.insert(assistantMsg);

                // 更新会话标题
                if (session.getTitle() == null || session.getTitle().isEmpty()) {
                    String title = req.getMessage().length() > 30 ?
                            req.getMessage().substring(0, 30) + "..." : req.getMessage();
                    session.setTitle(title);
                    chatSessionMapper.insert(session);
                }

                result.put("sessionId", session.getId());
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.complete();
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
                boolean isRetryFail = msg.contains("已重试");
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

    private ChatSession createNewSession() {
        ChatSession session = new ChatSession();
        session.setTitle("");
        session.setMode("ops");
        chatSessionMapper.insert(session);
        return session;
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions() {
        return chatSessionMapper.findAllByOrderByUpdatedAtDesc();
    }

    @GetMapping("/sessions/{id}")
    public List<ChatMessage> getSessionMessages(@PathVariable Long id) {
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
    public Map<String, Object> saveSkill(@RequestBody Skill skill) {
        skillLoader.insert(skill);
        return Map.of("status", "ok", "name", skill.getName());
    }

    @DeleteMapping("/skills/{name}")
    public Map<String, Object> deleteSkill(@PathVariable String name) {
        skillLoader.delete(name);
        return Map.of("status", "ok");
    }

    @PostMapping("/experience")
    public Map<String, Object> saveExperience(@RequestBody Skill skill) {
        if (skill.getName() == null || skill.getName().isBlank()) {
            return Map.of("status", "error", "message", "name 不能为空");
        }
        skillLoader.insert(skill);
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
}
