package com.middleware.manager.knowledge.web;

import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.knowledge.agent.ChatSession;
import com.middleware.manager.knowledge.agent.ChatSessionMapper;
import com.middleware.manager.repository.AdminAccountMapper;
import com.middleware.manager.security.GatewayAuthenticationToken;
import com.middleware.manager.security.PermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class AgentControllerSecurityTest {

    @Test
    @DisplayName("TC-AGENT-SEC-001 普通用户只可查看自己的会话")
    void nonAdminOnlyListsOwnSessions() {
        InMemorySessionMapper sessions = new InMemorySessionMapper();
        sessions.add(session(1L, 10L, "rag"));
        sessions.add(session(2L, 20L, "ops"));

        AgentController controller = controller(sessions);

        List<ChatSession> result = controller.getSessions(auth("alice"));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("TC-AGENT-SEC-002 普通用户不能修改他人的会话模式")
    void nonOwnerCannotChangeSessionMode() {
        InMemorySessionMapper sessions = new InMemorySessionMapper();
        sessions.add(session(1L, 20L, "rag"));

        AgentController controller = controller(sessions);

        assertThrows(NotFoundException.class,
                () -> controller.updateSessionMode(1L, java.util.Map.of("mode", "ops"), auth("alice")));
    }

    @Test
    @DisplayName("TC-AGENT-SEC-003 新会话记录签名身份中的当前用户")
    void createdSessionStoresCurrentUser() {
        InMemorySessionMapper sessions = new InMemorySessionMapper();
        AgentController controller = controller(sessions);

        ChatSession created = controller.createSession(java.util.Map.of("mode", "ops"), auth("alice"));

        assertEquals(10L, created.getCreatedBy());
        assertEquals("ops", created.getMode());
    }

    private AgentController controller(InMemorySessionMapper sessions) {
        AgentController controller = new AgentController();
        ReflectionTestUtils.setField(controller, "chatSessionMapper", sessions);
        ReflectionTestUtils.setField(controller, "adminAccountMapper", new StubAdminAccountMapper());
        ReflectionTestUtils.setField(controller, "permissionService", new PermissionService());
        return controller;
    }

    private static ChatSession session(Long id, Long createdBy, String mode) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setTitle("s" + id);
        session.setMode(mode);
        session.setCreatedBy(createdBy);
        return session;
    }

    private static Authentication auth(String username) {
        return GatewayAuthenticationToken.authenticated(
                username, username, List.of("ROLE_DEV_MGR"), null, false);
    }

    private static class InMemorySessionMapper implements ChatSessionMapper {
        private final ConcurrentMap<Long, ChatSession> store = new ConcurrentHashMap<>();
        private long nextId = 100L;

        void add(ChatSession session) {
            store.put(session.getId(), session);
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
                    .sorted(Comparator.comparing(ChatSession::getId))
                    .toList();
        }

        @Override
        public int insert(ChatSession session) {
            session.setId(nextId++);
            store.put(session.getId(), session);
            return 1;
        }

        @Override
        public int update(ChatSession session) {
            store.put(session.getId(), session);
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
            account.setId("alice".equals(username) ? 10L : 20L);
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
