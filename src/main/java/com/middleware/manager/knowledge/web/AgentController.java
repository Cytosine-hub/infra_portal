package com.middleware.manager.knowledge.web;

import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionRepository;
import com.middleware.manager.knowledge.agent.TroubleshootAgent;
import com.middleware.manager.knowledge.agent.TroubleshootAgent.AgentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private TroubleshootAgent agent;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    /**
     * POST /api/agent/chat
     * Request: { "sessionId": 1, "message": "Redis连接超时怎么排查" }
     * Response: { "answer": "...", "references": [...], "sessionId": 1 }
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        try {
            Long sessionId = request.getSessionId();
            if (sessionId == null) {
                ChatSession session = agent.createSession();
                sessionId = session.getId();
            }
            AgentResponse response = agent.chat(sessionId, request.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("answer", response.getAnswer());
            result.put("references", response.getReferences());
            result.put("sessionId", sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * GET /api/agent/sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getSessions() {
        return ResponseEntity.ok(agent.getAllSessions());
    }

    /**
     * GET /api/agent/sessions/{id}
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSessionMessages(@PathVariable Long id) {
        return ResponseEntity.ok(agent.getSessionMessages(id));
    }

    /**
     * POST /api/agent/sessions
     * 创建新会话，可指定 mode
     */
    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(@RequestBody(required = false) Map<String, String> body) {
        ChatSession session = new ChatSession();
        session.setTitle("");
        String mode = (body != null && body.get("mode") != null) ? body.get("mode") : "rag";
        session.setMode(mode);
        chatSessionRepository.save(session);
        return ResponseEntity.ok(session);
    }

    /**
     * PATCH /api/agent/sessions/{id}/mode
     */
    @PatchMapping("/sessions/{id}/mode")
    public ResponseEntity<?> updateSessionMode(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        if (mode == null || (!mode.equals("rag") && !mode.equals("ops"))) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "mode must be 'rag' or 'ops'");
            return ResponseEntity.badRequest().body(error);
        }
        return chatSessionRepository.findById(id)
                .map(session -> {
                    session.setMode(mode);
                    chatSessionRepository.save(session);
                    return ResponseEntity.ok((Object) session);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Request body for the chat endpoint.
     */
    public static class ChatRequest {
        private Long sessionId;
        private String message;

        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
