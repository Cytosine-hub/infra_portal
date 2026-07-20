package com.middleware.manager.agent.skill;

import java.util.List;
import java.util.Map;

public class Skill {
    private String name;
    private String description;
    private Trigger trigger;
    private List<Step> steps;

    public static class Trigger {
        private List<String> keywords;
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    }

    public static class Step {
        private String tool;
        private Map<String, String> args;
        private String prompt;
        private String description;

        public String getTool() { return tool; }
        public void setTool(String tool) { this.tool = tool; }
        public Map<String, String> getArgs() { return args; }
        public void setArgs(Map<String, String> args) { this.args = args; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Trigger getTrigger() { return trigger; }
    public void setTrigger(Trigger trigger) { this.trigger = trigger; }
    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }
}
