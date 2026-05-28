package com.middleware.manager.agent.service;

import com.middleware.manager.agent.model.ChatModel;
import com.middleware.manager.agent.model.ChatModel.Message;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String SYSTEM_PROMPT = """
            你是一个线上问题排查专家 Agent。你的职责是：
            1. 根据用户描述的问题或告警信息，分析可能的根因
            2. 使用可用的工具查询知识库、监控指标、日志等数据
            3. 综合分析数据，给出根因分析和修复建议
            4. 如果有排查经验匹配，优先参考历史经验

            可用工具：%s

            回复要求：
            - 先分析问题，再调用工具获取数据
            - 综合所有数据给出结论
            - 结论包含：根因、影响范围、修复建议
            - 如果需要执行操作，明确说明操作步骤和风险
            """;

    private final ChatModel chatModel;
    private final SkillLoader skillLoader;
    private final Map<String, Tool> toolMap;

    public AgentService(ChatModel chatModel, SkillLoader skillLoader, List<Tool> tools) {
        this.chatModel = chatModel;
        this.skillLoader = skillLoader;
        this.toolMap = tools.stream()
                .collect(Collectors.toMap(Tool::name, t -> t));
    }

    public Map<String, Object> chat(String userMessage, Map<String, String> context) {
        log.info("[Agent] Received: {}", userMessage);

        // 1. Try to match a skill
        Skill skill = skillLoader.match(userMessage);
        String response;
        String skillName = null;
        List<String> toolsUsed = new ArrayList<>();

        if (skill != null) {
            log.info("[Agent] Matched skill: {}", skill.getName());
            skillName = skill.getName();
            response = executeSkill(skill, context != null ? context : new HashMap<>(), toolsUsed);
        } else {
            // 2. General reasoning with tool awareness
            response = generalChat(userMessage);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", response);
        result.put("skill", skillName);
        result.put("toolsUsed", toolsUsed);
        return result;
    }

    private String executeSkill(Skill skill, Map<String, String> context, List<String> toolsUsed) {
        StringBuilder accumulated = new StringBuilder();
        accumulated.append("正在执行排查流程：").append(skill.getName()).append("\n\n");

        for (Skill.Step step : skill.getSteps()) {
            if (step.getTool() != null) {
                Tool tool = toolMap.get(step.getTool());
                if (tool == null) {
                    accumulated.append("[").append(step.getDescription()).append("] 工具不存在: ").append(step.getTool()).append("\n\n");
                    continue;
                }
                Map<String, Object> args = resolveArgs(step.getArgs(), context);
                log.info("[Agent] Calling tool: {} with args: {}", step.getTool(), args);
                try {
                    String result = tool.call(args);
                    toolsUsed.add(step.getTool());
                    accumulated.append("[").append(step.getDescription()).append("]\n").append(result).append("\n\n");
                } catch (Exception e) {
                    accumulated.append("[").append(step.getDescription()).append("] 调用失败: ").append(e.getMessage()).append("\n\n");
                }
            } else if (step.getPrompt() != null) {
                // Ask LLM to synthesize
                String prompt = accumulated + "\n" + resolveTemplate(step.getPrompt(), context);
                List<Message> messages = List.of(
                        Message.system("你是线上问题排查专家。根据以下数据综合分析，给出根因和修复建议。"),
                        Message.user(prompt)
                );
                return chatModel.generate(messages);
            }
        }

        // If no synthesis step, ask LLM to summarize
        List<Message> messages = List.of(
                Message.system("你是线上问题排查专家。根据以下排查数据，给出根因分析和修复建议。"),
                Message.user(accumulated.toString())
        );
        return chatModel.generate(messages);
    }

    private String generalChat(String userMessage) {
        String toolDesc = toolMap.values().stream()
                .map(t -> "- " + t.name() + ": " + t.description())
                .collect(Collectors.joining("\n"));

        List<Message> messages = List.of(
                Message.system(String.format(SYSTEM_PROMPT, toolDesc)),
                Message.user(userMessage)
        );
        return chatModel.generate(messages);
    }

    private Map<String, Object> resolveArgs(Map<String, String> template, Map<String, String> context) {
        if (template == null) return new HashMap<>();
        Map<String, Object> resolved = new HashMap<>();
        template.forEach((k, v) -> resolved.put(k, resolveTemplate(v, context)));
        return resolved;
    }

    private String resolveTemplate(String template, Map<String, String> context) {
        if (template == null || context == null) return template;
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
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
