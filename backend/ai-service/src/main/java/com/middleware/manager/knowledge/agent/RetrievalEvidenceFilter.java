package com.middleware.manager.knowledge.agent;

import com.middleware.manager.knowledge.service.KnowledgeSearchResult;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.service.WikiSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RetrievalEvidenceFilter {

    private static final Pattern TECHNICAL_TOKEN =
            Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{1,}");
    private static final Pattern CHINESE_SEQUENCE = Pattern.compile("[\\p{IsHan}]+");
    private static final Set<String> TECHNICAL_STOP_WORDS =
            Set.of("and", "or", "the", "how", "what", "is", "are", "vs");
    private static final List<String> QUESTION_PHRASES = List.of(
            "怎么办", "怎么处理", "怎么排查", "如何", "怎么", "请问", "是多少",
            "是什么", "有哪些", "用哪个", "哪个", "多少", "多大", "是否", "查看", "查询", "排查", "处理", "设置");
    private static final double MIN_CHINESE_COVERAGE = 0.5;
    private static final double MIN_ITEM_CHINESE_COVERAGE = 0.25;

    public EvidenceSelection select(String query,
                                    List<WikiSearchResult> wikiResults,
                                    List<KnowledgeSearchResult> knowledgeResults) {
        List<WikiSearchResult> safeWiki = wikiResults == null ? List.of() : wikiResults;
        List<KnowledgeSearchResult> safeKnowledge =
                knowledgeResults == null ? List.of() : knowledgeResults;

        String aggregate = aggregateText(safeWiki, safeKnowledge);
        Set<String> technicalTokens = technicalTokens(query);
        Set<String> chineseBigrams = chineseBigrams(query);

        // 入口只拦「确定无证据」这一种情形：问题里点名了技术标识，而全部上下文里
        // 一个都没出现。此时无论如何都答不了，直接拒答省掉一次模型调用。
        // 用 any 而非 all：跨文档比较类问题（如「Nginx 和 F5 的差异」）只召回其中
        // 一方时，部分回答仍然有价值，不该整体拒答。
        boolean technicalEvidenceMissing = !technicalTokens.isEmpty()
                && technicalTokens.stream().noneMatch(token -> containsIgnoreCase(aggregate, token));
        if (technicalEvidenceMissing) {
            return new EvidenceSelection(List.of(), List.of(), false);
        }

        // 不再用「聚合文本的中文覆盖率」做整体否决。词法重合度无法区分
        // 「同义不同词」（应放行）与「同词不同题」（应拒绝），拿它当否决条件会把
        // 稠密向量检索的核心收益抵消掉。可靠性改由「是否还剩下相关条目」判定，
        // 编造内容则交给出口侧的 AnswerGroundingVerifier 拦截。
        List<WikiSearchResult> filteredWiki = safeWiki.stream()
                .filter(result -> itemRelevant(wikiText(result), technicalTokens, chineseBigrams))
                .toList();
        List<KnowledgeSearchResult> filteredKnowledge = safeKnowledge.stream()
                .filter(result -> itemRelevant(knowledgeText(result), technicalTokens, chineseBigrams))
                .toList();
        boolean hasFilteredEvidence = !filteredWiki.isEmpty() || !filteredKnowledge.isEmpty();
        return new EvidenceSelection(filteredWiki, filteredKnowledge, hasFilteredEvidence);
    }

    private boolean itemRelevant(String text, Set<String> technicalTokens, Set<String> chineseBigrams) {
        boolean technicalMatch = technicalTokens.stream().anyMatch(token -> containsIgnoreCase(text, token));
        boolean chineseMatch = !chineseBigrams.isEmpty()
                && coverage(chineseBigrams, text) >= MIN_ITEM_CHINESE_COVERAGE;
        return technicalMatch || chineseMatch;
    }

    private String aggregateText(List<WikiSearchResult> wikiResults,
                                 List<KnowledgeSearchResult> knowledgeResults) {
        List<String> parts = new ArrayList<>();
        wikiResults.forEach(result -> parts.add(wikiText(result)));
        knowledgeResults.forEach(result -> parts.add(knowledgeText(result)));
        return String.join("\n", parts);
    }

    private String wikiText(WikiSearchResult result) {
        if (result == null || result.getPage() == null) {
            return "";
        }
        WikiPage page = result.getPage();
        return join(page.getTitle(), page.getSummary(), page.getContent());
    }

    private String knowledgeText(KnowledgeSearchResult result) {
        if (result == null) {
            return "";
        }
        return join(result.getSourceTitle(), result.getSectionPath(), result.getContent());
    }

    private String join(String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                present.add(value);
            }
        }
        return String.join("\n", present);
    }

    private Set<String> technicalTokens(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TECHNICAL_TOKEN.matcher(query == null ? "" : query);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (!TECHNICAL_STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Set<String> chineseBigrams(String query) {
        String normalized = query == null ? "" : query;
        for (String phrase : QUESTION_PHRASES) {
            normalized = normalized.replace(phrase, "");
        }
        normalized = normalized.replaceAll("[的了呢吗呀啊和与及定]", "");

        Set<String> bigrams = new LinkedHashSet<>();
        Matcher matcher = CHINESE_SEQUENCE.matcher(normalized);
        while (matcher.find()) {
            String sequence = matcher.group();
            if (sequence.length() == 1) {
                continue;
            }
            if (sequence.length() == 2) {
                bigrams.add(sequence);
                continue;
            }
            for (int i = 0; i < sequence.length() - 1; i++) {
                bigrams.add(sequence.substring(i, i + 2));
            }
        }
        return bigrams;
    }

    private double coverage(Set<String> terms, String text) {
        if (terms.isEmpty()) {
            return 1.0;
        }
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> matched = new HashSet<>();
        for (String term : terms) {
            if (normalized.contains(term.toLowerCase(Locale.ROOT))) {
                matched.add(term);
            }
        }
        return (double) matched.size() / terms.size();
    }

    private boolean containsIgnoreCase(String text, String token) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    public record EvidenceSelection(List<WikiSearchResult> wikiResults,
                                    List<KnowledgeSearchResult> knowledgeResults,
                                    boolean hasReliableEvidence) {
    }
}
