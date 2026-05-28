package com.middleware.manager.agent.web;

import com.middleware.manager.agent.service.AgentService;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatMessageRepository;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("opsAgentController")
@RequestMapping("/api/ops-agent")
public class AgentController {

    private final AgentService agentService;
    private final SkillLoader skillLoader;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public AgentController(AgentService agentService, SkillLoader skillLoader,
                           ChatSessionRepository chatSessionRepository,
                           ChatMessageRepository chatMessageRepository) {
        this.agentService = agentService;
        this.skillLoader = skillLoader;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        // 创建或获取会话
        Long sessionId = req.getSessionId();
        ChatSession session;
        if (sessionId != null) {
            session = chatSessionRepository.findById(sessionId)
                    .orElseGet(() -> createNewSession());
        } else {
            session = createNewSession();
        }

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("user");
        userMsg.setContent(req.getMessage());
        chatMessageRepository.save(userMsg);

        // 调用 Agent
        Map<String, Object> result = agentService.chat(req.getMessage(), req.getContext());

        // 保存助手消息
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(session.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent((String) result.get("response"));
        chatMessageRepository.save(assistantMsg);

        // 更新会话标题（使用第一条消息）
        if (session.getTitle() == null || session.getTitle().isEmpty()) {
            String title = req.getMessage().length() > 30 ?
                    req.getMessage().substring(0, 30) + "..." : req.getMessage();
            session.setTitle(title);
            chatSessionRepository.save(session);
        }

        result.put("sessionId", session.getId());
        return result;
    }

    private ChatSession createNewSession() {
        ChatSession session = new ChatSession();
        session.setTitle("");
        session.setMode("ops");
        return chatSessionRepository.save(session);
    }

    @GetMapping("/sessions")
    public List<ChatSession> getSessions() {
        return chatSessionRepository.findAllByOrderByUpdatedAtDesc();
    }

    @GetMapping("/sessions/{id}")
    public List<ChatMessage> getSessionMessages(@PathVariable Long id) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(id);
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
        skillLoader.save(skill);
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
}
