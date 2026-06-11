package com.middleware.manager.agent.service;

import com.middleware.manager.agent.model.ChatModel;
import com.middleware.manager.agent.model.ChatModel.Message;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.agent.tool.Tool;
import com.middleware.manager.agent.tool.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentService {
    private static final String SYSTEM_PROMPT = """
            你是一个线上问题排查专家 Agent。你的职责是：
            1. 根据用户描述的问题或告警信息，分析可能的根因
            2. 使用可用的工具查询知识库、监控指标、日志等数据
            3. 综合分析数据，给出根因分析和修复建议
            4. 如果有排查经验匹配，优先参考历史经验

            可用工具：%s

            回复要求：
            - 系统会预先提供一次知识库检索结果；先基于检索结果回答
            - 不要在回复正文中输出 <tool_call>、JSON 工具调用或伪代码工具调用
            - 综合所有数据给出结论
            - 结论包含：根因、影响范围、修复建议
            - 如果用户是在询问产品、概念或配置说明，可以按“概述、关键能力、适用场景、参考来源”组织
            - 如果需要执行操作，明确说明操作步骤和风险
            """;

    private final ChatModel chatModel;
    private final SkillLoader skillLoader;
    private final Map<String, Tool> toolMap;
    private final ToolGateway toolGateway;

    public AgentService(ChatModel chatModel, SkillLoader skillLoader, List<Tool> tools) {
        this(chatModel, skillLoader, tools, null);
    }

    @Autowired
    public AgentService(ChatModel chatModel, SkillLoader skillLoader, List<Tool> tools, ToolGateway toolGateway) {
        this.chatModel = chatModel;
        this.skillLoader = skillLoader;
        this.toolMap = tools.stream()
                .collect(Collectors.toMap(Tool::name, t -> t));
        this.toolGateway = toolGateway;
    }

    public Map<String, Object> chat(String userMessage, Map<String, String> context) {
        return chat(userMessage, context, null);
    }

    public Map<String, Object> chat(String userMessage, Map<String, String> context, Consumer<String> onRetry) {
        return chat(userMessage, context, onRetry, null, null, null);
    }

    public Map<String, Object> chat(String userMessage, Map<String, String> context, Consumer<String> onRetry,
                                    Long sessionId, Long actorId, Consumer<AgentEvent> onEvent) {
        log.info("[Agent] Received: {}", userMessage);

        // 1. Try to match a skill
        Skill skill = skillLoader.match(userMessage);
        String response;
        String skillName = null;
        List<String> toolsUsed = new ArrayList<>();

        if (skill != null) {
            log.info("[Agent] Matched skill: {}", skill.getName());
            skillName = skill.getName();
            emit(onEvent, AgentEvent.of("run_started", Map.of("skill", skillName)));
            response = executeSkill(skill, context != null ? context : new HashMap<>(), toolsUsed,
                    onRetry, sessionId, actorId, onEvent);
        } else {
            // 2. General reasoning with tool awareness
            emit(onEvent, AgentEvent.of("run_started", Map.of("skill", "")));
            response = generalChat(userMessage, onRetry, toolsUsed, sessionId, actorId, onEvent);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", response);
        result.put("skill", skillName);
        result.put("toolsUsed", toolsUsed);
        return result;
    }

    private String executeSkill(Skill skill, Map<String, String> context, List<String> toolsUsed,
                                Consumer<String> onRetry, Long sessionId, Long actorId,
                                Consumer<AgentEvent> onEvent) {
        StringBuilder accumulated = new StringBuilder();
        accumulated.append("正在执行排查流程：").append(skill.getName()).append("\n\n");

        for (Skill.Step step : skill.getSteps()) {
            if (step.getTool() != null) {
                String stepName = step.getDescription() != null ? step.getDescription() : step.getTool();
                Tool tool = toolMap.get(step.getTool());
                if (tool == null) {
                    accumulated.append("[").append(stepName).append("] 工具不存在: ").append(step.getTool()).append("\n\n");
                    emit(onEvent, AgentEvent.toolResult(stepName, step.getTool(), false,
                            "工具不存在: " + step.getTool(), 0L));
                    continue;
                }
                Map<String, Object> args = resolveArgs(step.getArgs(), context);
                List<String> missing = findUnresolved(args);
                if (!missing.isEmpty()) {
                    accumulated.append("[").append(stepName).append("] 缺少必要参数：")
                            .append(String.join(", ", missing)).append("，请补充后重试\n\n");
                    emit(onEvent, AgentEvent.toolResult(stepName, step.getTool(), false,
                            "缺少必要参数：" + String.join(", ", missing), 0L));
                    continue;
                }
                log.info("[Agent] Calling tool: {} argKeys={}", step.getTool(), args.keySet());
                emit(onEvent, AgentEvent.stepStarted(stepName, step.getTool()));
                ToolResult result = callTool(tool, args, sessionId, actorId, stepName);
                toolsUsed.add(step.getTool());
                accumulated.append("[").append(stepName).append("]\n")
                        .append(result.toPromptText()).append("\n\n");
                emit(onEvent, AgentEvent.toolResult(stepName, step.getTool(), result.isSuccess(),
                        result.getSummary(), result.getLatencyMs()));
            } else if (step.getPrompt() != null) {
                String stepName = step.getDescription() != null ? step.getDescription() : "综合分析";
                emit(onEvent, AgentEvent.stepStarted(stepName, "llm"));
                String prompt = accumulated + "\n" + resolveTemplate(step.getPrompt(), context);
                List<Message> messages = List.of(
                        Message.system("你是线上问题排查专家。根据以下数据综合分析，给出根因和修复建议。"),
                        Message.user(prompt)
                );
                return chatModel.generate(messages, onRetry);
            }
        }

        List<Message> messages = List.of(
                Message.system("你是线上问题排查专家。根据以下排查数据，给出根因分析和修复建议。"),
                Message.user(accumulated.toString())
        );
        return chatModel.generate(messages, onRetry);
    }

    private ToolResult callTool(Tool tool, Map<String, Object> args, Long sessionId, Long actorId, String stepName) {
        if (toolGateway != null) {
            return toolGateway.call(tool, args, sessionId, actorId, stepName);
        }
        long start = System.currentTimeMillis();
        try {
            String output = tool.call(args);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", output);
            return ToolResult.success(output, data, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("TOOL_EXECUTION_FAILED", "工具执行失败，请查看后台日志",
                    System.currentTimeMillis() - start);
        }
    }

    private String generalChat(String userMessage, Consumer<String> onRetry, List<String> toolsUsed,
                               Long sessionId, Long actorId, Consumer<AgentEvent> onEvent) {
        String toolDesc = toolMap.values().stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));
        String knowledgeContext = autoSearchKnowledge(userMessage, toolsUsed, sessionId, actorId, onEvent);

        List<Message> messages = List.of(
                Message.system(String.format(SYSTEM_PROMPT, toolDesc)),
                Message.user("用户问题：\n" + userMessage + "\n\n"
                        + "系统已自动执行 knowledge_search，检索结果如下：\n"
                        + knowledgeContext + "\n\n"
                        + "请直接基于以上 Wiki 和知识库内容回答。不要输出 <tool_call>。")
        );
        String answer = removeToolCallMarkup(chatModel.generate(messages, onRetry));
        if (answer.isBlank()) {
            return "已检索 Wiki 和知识库，相关内容如下：\n\n" + knowledgeContext;
        }
        return answer;
    }

    private String autoSearchKnowledge(String userMessage, List<String> toolsUsed, Long sessionId, Long actorId,
                                       Consumer<AgentEvent> onEvent) {
        Tool tool = toolMap.get("knowledge_search");
        if (tool == null) {
            return "knowledge_search 工具不可用";
        }
        String stepName = "检索 Wiki 和向量知识库";
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", userMessage);
        args.put("top_k", 6);
        emit(onEvent, AgentEvent.stepStarted(stepName, tool.name()));
        ToolResult result = callTool(tool, args, sessionId, actorId, stepName);
        toolsUsed.add(tool.name());
        emit(onEvent, AgentEvent.toolResult(stepName, tool.name(), result.isSuccess(),
                result.getSummary(), result.getLatencyMs()));
        return result.toPromptText();
    }

    private String removeToolCallMarkup(String response) {
        if (response == null) {
            return "";
        }
        return response
                .replaceAll("(?s)<tool_call>.*?</tool_call>", "")
                .trim();
    }

    private Map<String, Object> resolveArgs(Map<String, String> template, Map<String, String> context) {
        if (template == null) return new HashMap<>();
        Map<String, Object> resolved = new HashMap<>();
        template.forEach((k, v) -> resolved.put(k, resolveTemplate(v, context)));
        return resolved;
    }

    private List<String> findUnresolved(Map<String, Object> args) {
        List<String> missing = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{(\\w+)}}");
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getValue() instanceof String s) {
                java.util.regex.Matcher m = pattern.matcher(s);
                while (m.find()) {
                    missing.add(m.group(1));
                }
            }
        }
        return missing;
    }

    private String resolveTemplate(String template, Map<String, String> context) {
        if (template == null || context == null) return template;
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private void emit(Consumer<AgentEvent> sink, AgentEvent event) {
        if (sink != null) {
            sink.accept(event);
        }
    }

    public List<Map<String, Object>> listTools() {
        return toolMap.values().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t.name());
                    m.put("description", t.description());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
