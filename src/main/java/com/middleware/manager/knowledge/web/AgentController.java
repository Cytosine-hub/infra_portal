package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.knowledge.agent.TroubleshootAgent;
import com.middleware.manager.knowledge.agent.TroubleshootAgent.AgentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private TroubleshootAgent agent;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        sseExecutor.submit(() -> {
            try {
                Long sessionId = request.getSessionId();
                if (sessionId == null) {
                    ChatSession session = agent.createSession();
                    sessionId = session.getId();
                }

                Long finalSessionId = sessionId;
                AgentResponse response = agent.chat(sessionId, request.getMessage(), retryMsg -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("retry")
                                .data(Map.of("message", retryMsg)));
                    } catch (IOException ignored) {}
                });

                Map<String, Object> result = new HashMap<>();
                result.put("answer", response.getAnswer());
                result.put("references", response.getReferences());
                result.put("sessionId", finalSessionId);
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

    @GetMapping("/sessions")
    public List<ChatSession> getSessions() {
        return agent.getAllSessions();
    }

    @GetMapping("/sessions/{id}")
    public List<ChatMessage> getSessionMessages(@PathVariable Long id) {
        return agent.getSessionMessages(id);
    }

    @PostMapping("/sessions")
    public ChatSession createSession(@RequestBody(required = false) Map<String, String> body) {
        ChatSession session = new ChatSession();
        session.setTitle("");
        String mode = (body != null && body.get("mode") != null) ? body.get("mode") : "rag";
        session.setMode(mode);
        chatSessionMapper.insert(session);
        return session;
    }

    @PatchMapping("/sessions/{id}/mode")
    public Object updateSessionMode(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        if (mode == null || (!mode.equals("rag") && !mode.equals("ops"))) {
            return Map.of("error", "mode must be 'rag' or 'ops'");
        }
        ChatSession session = chatSessionMapper.findById(id);
        if (session == null) return null;
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
}
