package com.middleware.manager.knowledge.agent;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;

import com.google.gson.Gson;
import com.middleware.manager.knowledge.service.KnowledgeSearchPort;
import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.service.WikiSearchPort;
import com.middleware.manager.wiki.service.WikiSearchResult;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TroubleshootAgent {

    private static final String SYSTEM_PROMPT =
            "你是企业内部中间件知识库问答助手。你会收到用户问题以及系统检索到的 Wiki 和向量/关键词知识库内容。\n" +
            "必须遵守：\n" +
            "1. 优先基于提供的内部知识库内容回答，不要编造内部标准、参数、流程或版本信息。\n" +
            "2. 如果上下文来自 Wiki，引用时标注【Wiki：页面标题】；如果上下文来自知识库文档，引用时标注【知识库：来源标题】。\n" +
            "3. 如果知识库中完全没有相关信息，明确告知用户：'知识库中未找到相关内容，无法给出基于内部知识库的结论'。\n" +
            "4. 只有用户问题明显是故障、告警或线上异常排查时，才使用'问题诊断、排查步骤、解决方案'结构。\n" +
            "5. 对介绍、说明、是什么、有哪些、如何配置、使用场景等知识问答类问题，按'概述、关键能力/配置要点、适用场景、参考来源'组织，不要输出问题诊断。\n" +
            "6. 如果确需补充通用知识，只能放在'通用补充'中，并明确不是内部知识库依据。";

    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final int DEFAULT_SEARCH_TOP_K = 5;
    private static final int MAX_RETRIES = 5;
    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final Pattern TROUBLESHOOTING_INTENT = Pattern.compile(
            ".*(故障|报错|错误|异常|失败|超时|无法|不能|卡顿|变慢|宕机|不可用|告警|报警|排查|诊断|根因|恢复|重启|连接池|CPU|cpu|内存|OOM|oom|磁盘|日志).*");

    private final ChatModel chatModel;
    private final OpenAiStreamClient streamClient;

    @Autowired
    private KnowledgeSearchPort knowledgeService;

    @Autowired(required = false)
    private WikiSearchPort wikiSearchService;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    private final Gson gson = new Gson();

    public TroubleshootAgent(ChatModel chatModel, OpenAiStreamClient streamClient) {
        this.chatModel = chatModel;
        this.streamClient = streamClient;
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
        ChatContext context = prepareChatContext(sessionId, userMessage, authentication);

        // 4. Call LLM (with retry)
        log.info("Calling LLM for session {}, message count: {}", sessionId, context.messages().size());
        ChatResponse response = null;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = chatModel.chat(context.messages());
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
        saveAssistantMessage(sessionId, answer, context.references());

        // 6. Return response
        return new AgentResponse(answer, context.references());
    }

    @Transactional
    public AgentResponse chatStream(Long sessionId, String userMessage, Consumer<String> onRetry,
                                    Consumer<String> onDelta, Authentication authentication) {
        ChatContext context = prepareChatContext(sessionId, userMessage, authentication);
        AtomicBoolean deltaSent = new AtomicBoolean(false);
        try {
            log.info("Calling streaming LLM for session {}, message count: {}", sessionId, context.messages().size());
            String answer = streamClient.stream(context.messages(), delta -> {
                deltaSent.set(true);
                onDelta.accept(delta);
            });
            if (answer.isBlank()) {
                throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.LLM_RESPONSE_TIMEOUT);
            }
            saveAssistantMessage(sessionId, answer, context.references());
            return new AgentResponse(answer, context.references());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (isClientDisconnect(e)) {
                throw new IllegalStateException("client disconnected", e);
            }
            if (deltaSent.get()) {
                log.warn("Streaming LLM failed after partial response sessionId={} error={}", sessionId, e.getMessage());
                throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.LLM_RESPONSE_TIMEOUT);
            }
            log.warn("Streaming LLM failed, fallback to non-streaming chat sessionId={} error={}", sessionId, e.getMessage());
            if (onRetry != null) {
                onRetry.accept(ErrorMessages.LLM_STREAM_UNAVAILABLE);
            }
            return chatWithoutSavingUserMessage(sessionId, context, onRetry);
        }
    }

    private AgentResponse chatWithoutSavingUserMessage(Long sessionId, ChatContext context, Consumer<String> onRetry) {
        ChatResponse response = null;
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                response = chatModel.chat(context.messages());
                break;
            } catch (Exception e) {
                log.error("LLM fallback call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
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
                log.error("LLM fallback call exhausted retries", lastException);
            }
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.LLM_RESPONSE_TIMEOUT);
        }
        String answer = response.aiMessage() != null ? response.aiMessage().text() : "";
        saveAssistantMessage(sessionId, answer, context.references());
        return new AgentResponse(answer, context.references());
    }

    private void saveAssistantMessage(Long sessionId, String answer, List<Map<String, Object>> references) {
        com.middleware.manager.knowledge.agent.ChatMessage assistantMsg = new com.middleware.manager.knowledge.agent.ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setReferencesText(gson.toJson(references));
        chatMessageMapper.insert(assistantMsg);
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

    private boolean isClientDisconnect(Exception e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("client disconnected");
    }

    /**
     * Get all sessions ordered by last update.
     */
    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.findAllByOrderByUpdatedAtDesc();
    }

    private ChatContext prepareChatContext(Long sessionId, String userMessage, Authentication authentication) {
        com.middleware.manager.knowledge.agent.ChatMessage userMsg = new com.middleware.manager.knowledge.agent.ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chatMessageMapper.insert(userMsg);

        ChatSession session = chatSessionMapper.findById(sessionId);
        if (session == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if ("新会话".equals(session.getTitle()) || session.getTitle() == null || session.getTitle().isBlank()) {
            String title = userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage;
            session.setTitle(title);
            chatSessionMapper.update(session);
        }

        List<WikiSearchResult> wikiResults = Collections.emptyList();
        if (wikiSearchService != null) {
            try {
                wikiResults = wikiSearchService.search(userMessage, DEFAULT_SEARCH_TOP_K, authentication);
            } catch (Exception e) {
                log.warn("Wiki search failed: {}", e.getMessage());
            }
        }

        List<KnowledgeSearchResult> searchResults = knowledgeService.search(userMessage, DEFAULT_SEARCH_TOP_K);
        List<Map<String, Object>> references = buildReferences(wikiResults, searchResults);
        String contextMessage = buildHybridContextMessage(userMessage, wikiResults, searchResults);
        List<ChatMessage> messages = buildMessages(sessionId, contextMessage);
        return new ChatContext(messages, references);
    }

    private List<Map<String, Object>> buildReferences(List<WikiSearchResult> wikiResults,
                                                      List<KnowledgeSearchResult> searchResults) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (WikiSearchResult r : wikiResults) {
            Map<String, Object> ref = new HashMap<>();
            ref.put("title", r.getPage().getTitle());
            ref.put("wikiPageId", r.getPage().getId());
            ref.put("source", "wiki");
            references.add(ref);
        }
        for (KnowledgeSearchResult r : searchResults) {
            if (r.getSourceTitle() != null) {
                Map<String, Object> ref = new HashMap<>();
                ref.put("title", r.getSourceTitle());
                ref.put("source", r.getSource());
                references.add(ref);
            }
        }
        return references;
    }

    private List<ChatMessage> buildMessages(Long sessionId, String contextMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

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

        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
            messages.set(messages.size() - 1, new UserMessage(contextMessage));
        } else {
            messages.add(new UserMessage(contextMessage));
        }
        return messages;
    }

    private String buildHybridContextMessage(String userMessage,
            List<WikiSearchResult> wikiResults,
            List<KnowledgeSearchResult> knowledgeResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(userMessage).append("\n\n");
        sb.append("用户意图：").append(isTroubleshootingIntent(userMessage) ? "故障排查" : "知识问答").append("\n");
        sb.append("请按用户意图选择回答结构。\n\n");
        if (!wikiResults.isEmpty()) {
            sb.append("以下是 Wiki 知识库中的相关页面：\n\n");
            appendWikiContext(sb, wikiResults);
        }
        if (!knowledgeResults.isEmpty()) {
            sb.append("以下是向量/关键词知识库中的相关内容：\n");
            for (int i = 0; i < knowledgeResults.size(); i++) {
                KnowledgeSearchResult r = knowledgeResults.get(i);
                sb.append(String.format("【知识库 %d】来源：%s\n%s\n\n",
                        i + 1, r.getSourceTitle(), r.getContent()));
            }
        }
        if (wikiResults.isEmpty() && knowledgeResults.isEmpty()) {
            sb.append("知识库中未找到相关内容。\n");
        }
        return sb.toString();
    }

    private boolean isTroubleshootingIntent(String userMessage) {
        return userMessage != null && TROUBLESHOOTING_INTENT.matcher(userMessage).matches();
    }

    private void appendWikiContext(StringBuilder sb, List<WikiSearchResult> results) {
        int totalChars = sb.length();
        for (int i = 0; i < results.size(); i++) {
            WikiPage page = results.get(i).getPage();
            String content = page.getContent();
            if (content == null) content = "";

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

    private record ChatContext(List<ChatMessage> messages, List<Map<String, Object>> references) {
    }
}
