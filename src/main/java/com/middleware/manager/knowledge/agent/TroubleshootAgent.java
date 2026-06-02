package com.middleware.manager.knowledge.agent;

import com.google.gson.Gson;
import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.knowledge.service.KnowledgeService.SearchResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class TroubleshootAgent {

    private static final Logger log = LoggerFactory.getLogger(TroubleshootAgent.class);

    private static final String SYSTEM_PROMPT =
            "你是一个中间件故障排查专家。用户会描述遇到的问题，你需要：\n" +
            "1. 分析问题描述\n" +
            "2. 根据提供的知识库内容给出诊断\n" +
            "3. 给出具体的排查步骤和解决方案\n" +
            "4. 如果知识库中没有相关信息，基于你的经验给出建议\n\n" +
            "回答格式要求：\n" +
            "- 先给出问题诊断\n" +
            "- 再给出排查步骤\n" +
            "- 最后给出解决方案\n\n" +
            "信息来源标注规则（必须严格遵守）：\n" +
            "- 引用知识库内容时，在相关段落后标注【知识库：来源标题】\n" +
            "- 基于你自身知识补充的内容，在相关段落后标注【模型知识】\n" +
            "- 如果知识库中完全没有相关信息，明确告知用户：'知识库中未找到相关内容，以下基于模型通用知识给出建议'";

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int DEFAULT_SEARCH_TOP_K = 5;
    private static final int MAX_RETRIES = 5;

    private final ChatModel chatModel;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    private final Gson gson = new Gson();

    public TroubleshootAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Create a new chat session.
     */
    public ChatSession createSession() {
        ChatSession session = new ChatSession();
        session.setTitle("新会话");
        session.setMode("rag");
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * Core chat method: send a user message and get an agent response.
     */
    public AgentResponse chat(Long sessionId, String userMessage) {
        return chat(sessionId, userMessage, null);
    }

    public AgentResponse chat(Long sessionId, String userMessage, Consumer<String> onRetry) {
        // 1. Save user message
        com.middleware.manager.knowledge.agent.ChatMessage userMsg = new com.middleware.manager.knowledge.agent.ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chatMessageMapper.insert(userMsg);

        // Update session title from first message
        ChatSession session = chatSessionMapper.findById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        if ("新会话".equals(session.getTitle())) {
            String title = userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage;
            session.setTitle(title);
            chatSessionMapper.update(session);
        }

        // 2. Retrieve relevant knowledge
        List<SearchResult> searchResults = knowledgeService.search(userMessage, DEFAULT_SEARCH_TOP_K);
        List<String> references = new ArrayList<>();
        for (SearchResult r : searchResults) {
            if (r.getSourceTitle() != null) {
                references.add(r.getSourceTitle());
            }
        }

        // 3. Build messages list for LLM (LangChain4j messages)
        List<ChatMessage> messages = new ArrayList<>();

        // System prompt
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // History messages (up to MAX_HISTORY_MESSAGES)
        List<com.middleware.manager.knowledge.agent.ChatMessage> history =
                chatMessageMapper.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            com.middleware.manager.knowledge.agent.ChatMessage h = history.get(i);
            if ("user".equals(h.getRole())) {
                messages.add(new UserMessage(h.getContent()));
            } else if ("assistant".equals(h.getRole())) {
                messages.add(new AiMessage(h.getContent()));
            }
        }

        // Current question with knowledge context
        String contextMessage = buildContextMessage(userMessage, searchResults);
        // Replace the last user message with the context-enriched version
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
            messages.set(messages.size() - 1, new UserMessage(contextMessage));
        } else {
            messages.add(new UserMessage(contextMessage));
        }

        // 4. Call LLM (with retry)
        log.info("Calling LLM for session {}, message count: {}", sessionId, messages.size());
        ChatResponse response = null;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = chatModel.chat(messages);
                break;
            } catch (Exception e) {
                log.error("LLM call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    if (onRetry != null) {
                        onRetry.accept("模型响应超时，正在重试（" + attempt + "/" + MAX_RETRIES + "）...");
                    }
                    try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        if (response == null) {
            throw new RuntimeException("模型响应超时，已重试" + MAX_RETRIES + "次，请稍后再试", lastException);
        }

        String answer = response.aiMessage() != null ? response.aiMessage().text() : "";

        // 5. Save assistant message
        com.middleware.manager.knowledge.agent.ChatMessage assistantMsg = new com.middleware.manager.knowledge.agent.ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setReferencesText(gson.toJson(references));
        chatMessageMapper.insert(assistantMsg);

        // 6. Return response
        return new AgentResponse(answer, references);
    }

    /**
     * Get all messages for a session.
     */
    public List<com.middleware.manager.knowledge.agent.ChatMessage> getSessionMessages(Long sessionId) {
        return chatMessageMapper.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Get all sessions ordered by last update.
     */
    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.findAllByOrderByUpdatedAtDesc();
    }

    /**
     * Build a user message enriched with knowledge context.
     */
    private String buildContextMessage(String userMessage, List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(userMessage).append("\n\n");
        if (!results.isEmpty()) {
            sb.append("以下是知识库中的相关内容：\n");
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(String.format("【知识%d】来源：%s\n%s\n\n",
                        i + 1, r.getSourceTitle(), r.getContent()));
            }
        }
        return sb.toString();
    }

    /**
     * Response DTO returned by the agent.
     */
    public static class AgentResponse {
        private String answer;
        private List<String> references;

        public AgentResponse() {
        }

        public AgentResponse(String answer, List<String> references) {
            this.answer = answer;
            this.references = references;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public List<String> getReferences() {
            return references;
        }

        public void setReferences(List<String> references) {
            this.references = references;
        }
    }
}
