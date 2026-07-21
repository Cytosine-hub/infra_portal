package com.middleware.manager.agent.web;

import com.middleware.manager.agent.service.AgentService;
import com.middleware.manager.agent.service.AgentEvent;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.knowledge.agent.ChatMessage;
import com.middleware.manager.knowledge.agent.ChatMessageMapper;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.GatewayAuthenticationToken;
import com.middleware.manager.security.PermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class OpsAgentControllerSecurityTest {

    @Test
    @DisplayName("TC-OPS-SEC-001 普通用户不能保存技能")
    void ordinaryUserCannotSaveSkill() {
        AgentController controller = controller(new CountingSessionMapper());

        assertThrows(ForbiddenException.class,
                () -> controller.saveSkill(new Skill(), ordinaryAuth("alice")));
    }

    @Test
    @DisplayName("TC-OPS-SEC-002 专业管理员可按签名岗位身份保存技能")
    void categoryAdminCanSaveSkill() {
        RecordingSkillLoader skillLoader = new RecordingSkillLoader();
        AgentController controller = controller(new CountingSessionMapper(), skillLoader);
        Skill skill = new Skill();
        skill.setName("test-skill");

        var result = controller.saveSkill(skill, categoryAdminAuth("alice"));

        assertEquals("ok", result.get("status"));
        assertSame(skill, skillLoader.saved);
    }

    @Test
    @DisplayName("TC-OPS-SEC-003 系统管理员提交空技能名仍被业务校验拒绝")
    void blankSkillNameIsRejected() {
        AgentController controller = controller(new CountingSessionMapper());

        assertThrows(BusinessException.class,
                () -> controller.saveSkill(new Skill(), systemAdminAuth("alice")));
    }

    @Test
    @DisplayName("TC-OPS-SEC-004 更新会话标题不会重复插入会话")
    void titleUpdateDoesNotInsertSecondSession() {
        CountingSessionMapper sessions = new CountingSessionMapper();
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setMode("ops");
        session.setCreatedBy(10L);
        sessions.add(session);

        AgentController controller = controller(sessions, new RecordingSkillLoader(),
                new StubAgentService());
        AgentController.ChatRequest request = new AgentController.ChatRequest();
        request.setSessionId(1L);
        request.setMessage("hello");

        controller.chat(request, ordinaryAuth("alice"));

        waitUntil(() -> sessions.updateCount == 1);

        assertEquals(1, sessions.insertCount);
        assertEquals(1, sessions.updateCount);
        assertEquals(1, sessions.store.size());
    }

    private AgentController controller(CountingSessionMapper sessions) {
        return controller(sessions, new RecordingSkillLoader());
    }

    private AgentController controller(CountingSessionMapper sessions, SkillLoader skillLoader) {
        return controller(sessions, skillLoader, null);
    }

    private AgentController controller(CountingSessionMapper sessions, SkillLoader skillLoader,
                                       AgentService agentService) {
        return new AgentController(agentService, skillLoader, sessions, new NoopMessageMapper(),
                new StubAdminAccountMapper(), new PermissionService());
    }

    private static Authentication ordinaryAuth(String username) {
        return GatewayAuthenticationToken.authenticated(
                username, username, List.of("ROLE_DEV_MGR"), null, false);
    }

    private static Authentication categoryAdminAuth(String username) {
        return GatewayAuthenticationToken.authenticated(
                username, username, List.of("ROLE_MIDDLEWARE_ADMIN"), "中间件", true);
    }

    private static Authentication systemAdminAuth(String username) {
        return GatewayAuthenticationToken.authenticated(
                username, username, List.of("ROLE_SYS_ADMIN"), null, false);
    }

    private static class RecordingSkillLoader extends SkillLoader {
        private Skill saved;

        @Override
        public void save(Skill skill) {
            this.saved = skill;
        }
    }

    private static class StubAgentService extends AgentService {
        StubAgentService() {
            super(null, null, List.of());
        }

        @Override
        public Map<String, Object> chat(String userMessage, Map<String, String> context,
                                        Consumer<String> onRetry) {
            return response();
        }

        @Override
        public Map<String, Object> chat(String userMessage, Map<String, String> context,
                                        Consumer<String> onRetry, Long sessionId, Long actorId,
                                        Consumer<AgentEvent> onEvent) {
            return response();
        }

        private Map<String, Object> response() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("response", "ok");
            result.put("skill", null);
            result.put("toolsUsed", List.of());
            return result;
        }
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 1000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static class CountingSessionMapper implements ChatSessionMapper {
        private final ConcurrentMap<Long, ChatSession> store = new ConcurrentHashMap<>();
        private int insertCount;
        private int updateCount;
        private long nextId = 1L;

        void add(ChatSession session) {
            store.put(session.getId(), session);
            nextId = Math.max(nextId, session.getId() + 1);
            insertCount++;
        }

        @Override
        public ChatSession findById(Long id) {
            return store.get(id);
        }

        @Override
        public ChatSession findByIdAndCreatedBy(Long id, Long createdBy) {
            ChatSession session = store.get(id);
            return session != null && createdBy.equals(session.getCreatedBy()) ? session : null;
        }

        @Override
        public List<ChatSession> findAllByOrderByUpdatedAtDesc() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<ChatSession> findByCreatedByOrderByUpdatedAtDesc(Long createdBy) {
            return store.values().stream()
                    .filter(session -> createdBy.equals(session.getCreatedBy()))
                    .toList();
        }

        @Override
        public int insert(ChatSession session) {
            session.setId(nextId++);
            store.put(session.getId(), session);
            insertCount++;
            return 1;
        }

        @Override
        public int update(ChatSession session) {
            store.put(session.getId(), session);
            updateCount++;
            return 1;
        }
    }

    private static class NoopMessageMapper implements ChatMessageMapper {
        @Override
        public List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId) {
            return List.of();
        }

        @Override
        public int insert(ChatMessage message) {
            return 1;
        }
    }

    private static class StubAdminAccountMapper implements AdminAccountMapper {
        @Override
        public AdminAccount findById(Long id) {
            return null;
        }

        @Override
        public AdminAccount findByUsername(String username) {
            AdminAccount account = new AdminAccount();
            account.setId(10L);
            account.setUsername(username);
            return account;
        }

        @Override
        public List<AdminAccount> findAllByOrderByCreatedAtAsc() {
            return List.of();
        }

        @Override
        public long countByRole(String role) {
            return 0;
        }

        @Override
        public int insert(AdminAccount account) {
            return 0;
        }

        @Override
        public int update(AdminAccount account) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            return 0;
        }

        @Override
        public long count() {
            return 0;
        }
    }

}
