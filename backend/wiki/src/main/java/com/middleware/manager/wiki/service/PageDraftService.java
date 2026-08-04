package com.middleware.manager.wiki.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 经验页面轻量起草：从一份源文档生成 Markdown 草稿，供人工修改后保存。
 * <p>取代已下线的 LLM 编译流水线——那条流水线是「LLM 直接产出最终内容」，
 * 有损、不确定且昂贵（一份 364K PDF 需 7.5 分钟 / 4 万 token）。这里只做一次
 * LLM 调用产出草稿，最终内容由人来定，LLM 不进入内容层的真相链路。
 */
@Service
@Slf4j
public class PageDraftService {

    private static final String SYSTEM_PROMPT = """
            你是基础设施运维知识库的写作助手。基于给定的源文档片段，为指定主题起草一篇 Markdown 经验页面。
            要求：
            1. 只使用源文档中出现的事实，不要补充源文档里没有的命令、参数、指标或阈值
            2. 结构清晰，按「现象 / 排查步骤 / 处理方案 / 注意事项」组织，没有对应内容的小节直接省略
            3. 标题不要包含软件名和版本号，这些由页面标签承载
            4. 只输出 Markdown 正文，不要输出解释性文字
            """;

    private final WikiSourceMapper sourceMapper;
    private final ChatModel chatModel;
    private final int maxSourceChars;

    public PageDraftService(WikiSourceMapper sourceMapper,
                            ChatModel chatModel,
                            @Value("${app.wiki.draft.max-source-chars:12000}") int maxSourceChars) {
        this.sourceMapper = sourceMapper;
        this.chatModel = chatModel;
        this.maxSourceChars = maxSourceChars;
    }

    public String draft(Long sourceId, String topic) {
        WikiSource source = sourceMapper.findById(sourceId);
        if (source == null) {
            throw new NotFoundException(ErrorCode.WIKI_SOURCE_NOT_FOUND, ErrorMessages.WIKI_SOURCE_NOT_FOUND);
        }
        String content = source.getContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID);
        }

        String excerpt = content.length() > maxSourceChars ? content.substring(0, maxSourceChars) : content;
        List<ChatMessage> messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(buildUserPrompt(source, topic, excerpt)));

        String reply = chatModel.chat(messages).aiMessage().text();
        String draft = stripCodeFence(reply);
        if (draft.isBlank()) {
            log.warn("起草返回空内容 sourceId={} topic={}", sourceId, topic);
            throw new BusinessException(ErrorCode.WIKI_DRAFT_FAILED, ErrorMessages.WIKI_DRAFT_FAILED);
        }
        log.info("已生成草稿 sourceId={} topic={} 长度={}", sourceId, topic, draft.length());
        return draft;
    }

    private String buildUserPrompt(WikiSource source, String topic, String excerpt) {
        return """
                ## 主题
                %s

                ## 源文档
                标题：%s
                分类：%s
                软件：%s

                ## 源文档片段
                %s
                """.formatted(
                topic == null || topic.isBlank() ? source.getTitle() : topic,
                source.getTitle(),
                source.getCategory() == null ? "未标注" : source.getCategory(),
                source.getSoftware() == null ? "未标注" : source.getSoftware(),
                excerpt);
    }

    /** 模型常把整篇 Markdown 包在 ```markdown 围栏里，保存前剥掉。 */
    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return "";
        }
        String body = trimmed.substring(firstNewline + 1);
        int closing = body.lastIndexOf("```");
        return (closing >= 0 ? body.substring(0, closing) : body).strip();
    }
}
