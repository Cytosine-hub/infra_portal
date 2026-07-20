package com.middleware.manager.agent.service;

import com.middleware.manager.agent.domain.AgentToolInvocation;
import com.middleware.manager.agent.model.ChatModel;
import com.middleware.manager.agent.repository.AgentToolInvocationMapper;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.agent.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceTest {

    @Test
    void skillToolCallEmitsEventsAndAuditsInvocation() {
        Skill skill = skill("test-skill", toolStep("查询知识", "knowledge_search"), promptStep());
        RecordingInvocationMapper invocationMapper = new RecordingInvocationMapper();
        AgentService service = service(skill, new StaticTool("knowledge_search", "知识库命中"), invocationMapper);
        List<AgentEvent> events = new ArrayList<>();

        Map<String, Object> result = service.chat("redis cpu高", Map.of(), null, 7L, 10L, events::add);

        assertEquals("final answer", result.get("response"));
        assertEquals("test-skill", result.get("skill"));
        assertEquals(List.of("knowledge_search"), result.get("toolsUsed"));
        assertTrue(events.stream().anyMatch(event -> "run_started".equals(event.getType())));
        assertTrue(events.stream().anyMatch(event -> "step_started".equals(event.getType())));
        assertTrue(events.stream().anyMatch(event -> "tool_result".equals(event.getType())
                && Boolean.TRUE.equals(event.getPayload().get("success"))));
        assertEquals(1, invocationMapper.inserted.size());
        assertEquals("SUCCESS", invocationMapper.inserted.get(0).getStatus());
    }

    @Test
    void failedToolCallIsRecordedAndStillSummarized() {
        Skill skill = skill("test-skill", toolStep("查询日志", "search_logs"), promptStep());
        RecordingInvocationMapper invocationMapper = new RecordingInvocationMapper();
        AgentService service = service(skill, new FailingTool("search_logs"), invocationMapper);
        List<AgentEvent> events = new ArrayList<>();

        Map<String, Object> result = service.chat("redis cpu高", Map.of(), null, 7L, 10L, events::add);

        assertEquals("final answer", result.get("response"));
        assertEquals(List.of("search_logs"), result.get("toolsUsed"));
        assertTrue(events.stream().anyMatch(event -> "tool_result".equals(event.getType())
                && Boolean.FALSE.equals(event.getPayload().get("success"))));
        assertEquals(1, invocationMapper.inserted.size());
        assertEquals("FAILED", invocationMapper.inserted.get(0).getStatus());
    }

    @Test
    void toolGatewayRedactsSensitiveAuditFields() {
        RecordingInvocationMapper invocationMapper = new RecordingInvocationMapper();
        ToolGateway gateway = new ToolGateway(new AgentToolInvocationService(invocationMapper));

        gateway.call(new StaticTool("cmdb_query", "ok"),
                Map.of("apiToken", "secret-token", "filter", Map.of("password", "secret-password")),
                7L, 10L, "查询 CMDB");

        assertEquals(1, invocationMapper.inserted.size());
        String requestJson = invocationMapper.inserted.get(0).getRequestJson();
        assertTrue(requestJson.contains("\"apiToken\":\"***\""));
        assertTrue(requestJson.contains("\"password\":\"***\""));
        assertFalse(requestJson.contains("secret-token"));
        assertFalse(requestJson.contains("secret-password"));
    }

    private AgentService service(Skill skill, Tool tool, AgentToolInvocationMapper mapper) {
        ToolGateway gateway = new ToolGateway(new AgentToolInvocationService(mapper));
        return new AgentService(new StaticChatModel(), new SingleSkillLoader(skill), List.of(tool), gateway);
    }

    private Skill skill(String name, Skill.Step... steps) {
        Skill skill = new Skill();
        skill.setName(name);
        Skill.Trigger trigger = new Skill.Trigger();
        trigger.setKeywords(List.of("redis"));
        skill.setTrigger(trigger);
        skill.setSteps(List.of(steps));
        return skill;
    }

    private Skill.Step toolStep(String description, String toolName) {
        Skill.Step step = new Skill.Step();
        step.setDescription(description);
        step.setTool(toolName);
        step.setArgs(Map.of("query", "redis cpu"));
        return step;
    }

    private Skill.Step promptStep() {
        Skill.Step step = new Skill.Step();
        step.setDescription("综合分析");
        step.setPrompt("请总结");
        return step;
    }

    private static class SingleSkillLoader extends SkillLoader {
        private final Skill skill;

        private SingleSkillLoader(Skill skill) {
            this.skill = skill;
        }

        @Override
        public Skill match(String input) {
            return skill;
        }
    }

    private static class StaticChatModel implements ChatModel {
        @Override
        public String generate(List<Message> messages) {
            return "final answer";
        }
    }

    private record StaticTool(String name, String output) implements Tool {
        @Override
        public String description() {
            return "static tool";
        }

        @Override
        public String call(Map<String, Object> params) {
            return output;
        }
    }

    private record FailingTool(String name) implements Tool {
        @Override
        public String description() {
            return "failing tool";
        }

        @Override
        public String call(Map<String, Object> params) {
            throw new IllegalStateException("backend unavailable");
        }
    }

    private static class RecordingInvocationMapper implements AgentToolInvocationMapper {
        private final List<AgentToolInvocation> inserted = new ArrayList<>();

        @Override
        public int insert(AgentToolInvocation invocation) {
            inserted.add(invocation);
            return 1;
        }
    }
}
