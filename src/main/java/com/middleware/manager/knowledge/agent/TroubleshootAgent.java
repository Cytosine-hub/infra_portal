package com.middleware.manager.knowledge.agent;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;

import com.google.gson.Gson;
import com.middleware.manager.knowledge.service.KnowledgeService;
import com.middleware.manager.knowledge.service.KnowledgeService.SearchResult;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.service.WikiSearchService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TroubleshootAgent {

    private static final String SYSTEM_PROMPT =
            "你是一个中间件故障排查专家。用户会描述遇到的问题，你需要：\n" +
            "1. 分析问题描述\n" +
            "2. 根据提供的知识库内容给出诊断\n" +
            "3. 给出具体的排查步骤和解决方案\n" +
            "4. 如果知识库中没有相关信息，明确说明未找到，不要编造内部标准、参数或流程\n\n" +
            "回答格式要求：\n" +
            "- 先给出问题诊断\n" +
            "- 再给出排查步骤\n" +
            "- 最后给出解决方案\n\n" +
            "信息来源标注规则（必须严格遵守）：\n" +
            "- 如果上下文来自 Wiki 知识库，引用时标注【Wiki：页面标题】\n" +
            "- 如果上下文来自知识库文档，引用时标注【知识库：来源标题】\n" +
            "- 不要使用未提供的内部知识；如确需通用排查方向，只能写成'通用排查建议'并明确非知识库依据\n" +
            "- 如果知识库中完全没有相关信息，明确告知用户：'知识库中未找到相关内容，无法给出基于内部知识库的结论'";

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int DEFAULT_SEARCH_TOP_K = 5;
    private static final int MAX_RETRIES = 5;
    private static final int MAX_CONTEXT_CHARS = 6000;

    private final ChatModel chatModel;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired(required = false)
    private WikiSearchService wikiSearchService;

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
        return createSession(null);
    }

    @Transactional
    public ChatSession createSession(Long createdBy) {
        ChatSession session = new ChatSession();
        session.setTitle("新会话");
        session.setMode("rag");
        session.setCreatedBy(createdBy);
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
        return chat(sessionId, userMessage, onRetry, null);
    }

    @Transactional
    public AgentResponse chat(Long sessionId, String userMessage, Consumer<String> onRetry,
                              Authentication authentication) {
        // 1. Save user message
        com.middleware.manager.knowledge.agent.ChatMessage userMsg = new com.middleware.manager.knowledge.agent.ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chatMessageMapper.insert(userMsg);

        // Update session title from first message
        ChatSession session = chatSessionMapper.findById(sessionId);
        if (session == null) {
            throw new com.middleware.manager.exception.NotFoundException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if ("新会话".equals(session.getTitle())) {
            String title = userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage;
            session.setTitle(title);
            chatSessionMapper.update(session);
        }

        // 2. Retrieve relevant knowledge — Wiki first, chunk fallback
        List<Map<String, Object>> references = new ArrayList<>();
        String contextMessage;

        // Try Wiki search first
        List<WikiSearchService.WikiSearchResult> wikiResults = Collections.emptyList();
        if (wikiSearchService != null) {
            try {
                wikiResults = wikiSearchService.search(userMessage, DEFAULT_SEARCH_TOP_K, authentication);
            } catch (Exception e) {
                log.warn("Wiki search failed, falling back to chunk search: {}", e.getMessage());
            }
        }

        if (!wikiResults.isEmpty()) {
            // Wiki has sufficient results — use Wiki context
            contextMessage = buildWikiContextMessage(userMessage, wikiResults);
            for (WikiSearchService.WikiSearchResult r : wikiResults) {
                Map<String, Object> ref = new HashMap<>();
                ref.put("title", r.getPage().getTitle());
                ref.put("wikiPageId", r.getPage().getId());
                references.add(ref);
            }
        } else {
            // Fall back to existing chunk-based search
            List<SearchResult> searchResults = knowledgeService.search(userMessage, DEFAULT_SEARCH_TOP_K);
            contextMessage = buildContextMessage(userMessage, searchResults);
            for (SearchResult r : searchResults) {
                if (r.getSourceTitle() != null) {
                    Map<String, Object> ref = new HashMap<>();
                    ref.put("title", r.getSourceTitle());
                    references.add(ref);
                }
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
                if (isNonRetryableLlmFailure(e)) {
                    throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.LLM_AUTH_FAILED);
                }
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
            if (lastException != null) {
                log.error("LLM call exhausted retries", lastException);
            }
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.LLM_RESPONSE_TIMEOUT);
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

    private boolean isNonRetryableLlmFailure(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("401")
                || lower.contains("403")
                || lower.contains("unauthorized")
                || lower.contains("forbidden")
                || lower.contains("invalid api key")
                || lower.contains("api key");
    }

    /**
     * Get all sessions ordered by last update.
     */
    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.findAllByOrderByUpdatedAtDesc();
    }

    /**
     * Build a user message enriched with Wiki context.
     */
    private String buildWikiContextMessage(String userMessage,
            List<WikiSearchService.WikiSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(userMessage).append("\n\n");
        if (!results.isEmpty()) {
            sb.append("以下是 Wiki 知识库中的相关页面：\n\n");
            int totalChars = sb.length();
            for (int i = 0; i < results.size(); i++) {
                WikiPage page = results.get(i).getPage();
                String content = page.getContent();
                if (content == null) content = "";

                // Truncate if running out of budget
                int remaining = MAX_CONTEXT_CHARS - totalChars - 200;
                if (remaining <= 0) break;
                if (content.length() > remaining) {
                    content = content.substring(0, remaining) + "...(truncated)";
                }

                String entry = String.format("【Wiki %d】%s (类型:%s, 分类:%s)\n%s\n",
                        i + 1,
                        page.getTitle(),
                        page.getPageType() != null ? page.getPageType() : "未知",
                        page.getCategory() != null ? page.getCategory() : "通用",
                        content);
                sb.append(entry);
                totalChars += entry.length();

                // Show related page titles
                List<String> related = results.get(i).getRelatedPageTitles();
                if (related != null && !related.isEmpty()) {
                    String relatedLine = "关联页面: " + String.join(", ", related) + "\n\n";
                    sb.append(relatedLine);
                    totalChars += relatedLine.length();
                } else {
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
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
        private List<Map<String, Object>> references;

        public AgentResponse() {
        }

        public AgentResponse(String answer, List<Map<String, Object>> references) {
            this.answer = answer;
            this.references = references;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public List<Map<String, Object>> getReferences() {
            return references;
        }

        public void setReferences(List<Map<String, Object>> references) {
            this.references = references;
        }
    }
}
