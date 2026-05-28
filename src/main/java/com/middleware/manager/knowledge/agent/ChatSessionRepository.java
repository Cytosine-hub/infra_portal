package com.middleware.manager.knowledge.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ChatSession> rowMapper = (rs, rowNum) -> {
        ChatSession s = new ChatSession();
        s.setId(rs.getLong("id"));
        s.setTitle(rs.getString("title"));
        s.setMode(rs.getString("mode"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            s.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            s.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return s;
    };

    public ChatSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatSession save(ChatSession s) {
        if (s.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO chat_sessions (title, mode, created_at, updated_at) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, s.getTitle());
                ps.setString(2, s.getMode() != null ? s.getMode() : "rag");
                Timestamp now = Timestamp.valueOf(java.time.LocalDateTime.now());
                ps.setTimestamp(3, s.getCreatedAt() != null ? Timestamp.valueOf(s.getCreatedAt()) : now);
                ps.setTimestamp(4, s.getUpdatedAt() != null ? Timestamp.valueOf(s.getUpdatedAt()) : now);
                return ps;
            }, keyHolder);
            s.setId(keyHolder.getKey().longValue());
        } else {
            jdbcTemplate.update(
                    "UPDATE chat_sessions SET title = ?, mode = ?, updated_at = ? WHERE id = ?",
                    s.getTitle(), s.getMode() != null ? s.getMode() : "rag",
                    Timestamp.valueOf(java.time.LocalDateTime.now()), s.getId()
            );
        }
        return s;
    }

    public Optional<ChatSession> findById(Long id) {
        List<ChatSession> list = jdbcTemplate.query(
                "SELECT * FROM chat_sessions WHERE id = ?",
                rowMapper,
                id
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<ChatSession> findAllByOrderByUpdatedAtDesc() {
        return jdbcTemplate.query(
                "SELECT * FROM chat_sessions ORDER BY updated_at DESC",
                rowMapper
        );
    }
}
