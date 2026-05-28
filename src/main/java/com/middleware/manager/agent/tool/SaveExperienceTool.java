package com.middleware.manager.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SaveExperienceTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SaveExperienceTool.class);
    private final SkillLoader skillLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SaveExperienceTool(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @Override
    public String name() {
        return "save_experience";
    }

    @Override
    public String description() {
        return "将排查经验保存为可复用的 Skill。参数：name(Skill名称), description(描述), keywords(逗号分隔关键词), steps_json(JSON步骤数组)";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String call(Map<String, Object> params) {
        String name = (String) params.get("name");
        String description = (String) params.get("description");
        String keywordsStr = (String) params.get("keywords");
        String stepsJson = (String) params.get("steps_json");

        if (name == null || name.isBlank()) {
            return "错误：name 不能为空";
        }

        // 构建 Skill
        Skill skill = new Skill();
        skill.setName(name.trim().toLowerCase().replaceAll("[^a-z0-9\\-]", "-"));
        skill.setDescription(description != null ? description : "");

        // 关键词
        if (keywordsStr != null && !keywordsStr.isBlank()) {
            Skill.Trigger trigger = new Skill.Trigger();
            List<String> keywords = Arrays.stream(keywordsStr.split("[,，]"))
                    .map(String::trim)
                    .filter(k -> !k.isEmpty())
                    .toList();
            trigger.setKeywords(keywords);
            skill.setTrigger(trigger);
        }

        // 步骤
        if (stepsJson != null && !stepsJson.isBlank()) {
            try {
                List<Map<String, Object>> rawSteps = objectMapper.readValue(stepsJson,
                        new TypeReference<>() {});
                List<Skill.Step> steps = new ArrayList<>();
                for (Map<String, Object> raw : rawSteps) {
                    Skill.Step step = new Skill.Step();
                    step.setDescription((String) raw.get("description"));
                    if (raw.containsKey("tool")) {
                        step.setTool((String) raw.get("tool"));
                        if (raw.containsKey("args")) {
                            Map<String, Object> rawArgs = (Map<String, Object>) raw.get("args");
                            Map<String, String> args = new HashMap<>();
                            rawArgs.forEach((k, v) -> args.put(k, String.valueOf(v)));
                            step.setArgs(args);
                        }
                    } else if (raw.containsKey("prompt")) {
                        step.setPrompt((String) raw.get("prompt"));
                    }
                    steps.add(step);
                }
                skill.setSteps(steps);
            } catch (Exception e) {
                return "错误：steps_json 解析失败: " + e.getMessage();
            }
        } else {
            // 默认：一步 LLM 综合分析
            Skill.Step step = new Skill.Step();
            step.setDescription("综合分析");
            step.setPrompt("根据以上排查数据，给出根因分析和修复建议。");
            skill.setSteps(List.of(step));
        }

        // 保存
        try {
            skillLoader.save(skill);
            return "经验已保存为 Skill: " + skill.getName() + "，下次遇到相关问题将自动触发。";
        } catch (Exception e) {
            return "保存失败: " + e.getMessage();
        }
    }
}
