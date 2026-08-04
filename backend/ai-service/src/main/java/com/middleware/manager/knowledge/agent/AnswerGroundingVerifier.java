package com.middleware.manager.knowledge.agent;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 答案落地性校验（出口侧防幻觉）。
 * <p>幻觉在运维问答里的真实形态，是模型编造出上下文中不存在的**技术标识**：参数名、
 * 错误码、命令、配置项。输入侧的词法覆盖门禁抓不准这个——它会把「用户口语提问命中
 * 技术表述」这类语义检索的正常收益一并误杀，等于用「什么都答不了」换掉「偶尔答错」。
 * <p>这里只做一件事：把答案里出现、却既不在检索上下文、也不在用户问题中的技术标识
 * 挑出来。这类标识没有任何来源，只能是模型自己造的。
 */
@Component
public class AnswerGroundingVerifier {

    /** 具备"具体断言"性质的标识：带下划线/点/连字符的配置项、错误码等。单纯的产品名不算。 */
    private static final Pattern SPECIFIC_IDENTIFIER =
            Pattern.compile("[A-Za-z][A-Za-z0-9]*(?:[_.-][A-Za-z0-9]+)+");

    /** 常见的非断言性技术词，出现在答案里不构成捏造。 */
    private static final Set<String> BENIGN = Set.of(
            "e.g.", "i.e.", "etc.", "http.", "https.", "n/a");

    public GroundingResult verify(String question, String answer, String context) {
        String safeAnswer = answer == null ? "" : answer;
        String haystack = (context == null ? "" : context) + "\n" + (question == null ? "" : question);
        String normalizedHaystack = haystack.toLowerCase(Locale.ROOT);

        Set<String> ungrounded = new LinkedHashSet<>();
        Matcher matcher = SPECIFIC_IDENTIFIER.matcher(safeAnswer);
        while (matcher.find()) {
            String token = matcher.group();
            if (BENIGN.contains(token.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!normalizedHaystack.contains(token.toLowerCase(Locale.ROOT))) {
                ungrounded.add(token);
            }
        }
        return new GroundingResult(ungrounded.isEmpty(), List.copyOf(ungrounded));
    }

    /**
     * @param grounded          答案中的技术标识是否都有出处
     * @param ungroundedTokens  无出处的标识，用于日志与前端提示
     */
    public record GroundingResult(boolean grounded, List<String> ungroundedTokens) {
    }
}
