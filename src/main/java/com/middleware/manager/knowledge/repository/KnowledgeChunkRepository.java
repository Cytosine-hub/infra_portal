package com.middleware.manager.knowledge.repository;

import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class KnowledgeChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<KnowledgeChunk> rowMapper = (rs, rowNum) -> {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(rs.getLong("id"));
        c.setContent(rs.getString("content"));
        c.setSourceTitle(rs.getString("source_title"));
        c.setSourceType(rs.getString("source_type"));
        c.setSourceId(rs.getLong("source_id"));
        if (rs.wasNull()) {
            c.setSourceId(null);
        }
        c.setCategory(rs.getString("category"));
        c.setSoftware(rs.getString("software"));
        c.setChunkIndex(rs.getInt("chunk_index"));
        c.setVectorId(rs.getString("vector_id"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            c.setCreatedAt(ts.toLocalDateTime());
        }
        return c;
    };

    public KnowledgeChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KnowledgeChunk save(KnowledgeChunk c) {
        if (c.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO knowledge_chunks (content, source_title, source_type, source_id, category, software, chunk_index, vector_id, created_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, c.getContent());
                ps.setString(2, c.getSourceTitle());
                ps.setString(3, c.getSourceType());
                if (c.getSourceId() != null) {
                    ps.setLong(4, c.getSourceId());
                } else {
                    ps.setNull(4, java.sql.Types.BIGINT);
                }
                ps.setString(5, c.getCategory());
                ps.setString(6, c.getSoftware());
                ps.setInt(7, c.getChunkIndex());
                ps.setString(8, c.getVectorId());
                ps.setTimestamp(9, c.getCreatedAt() != null ? Timestamp.valueOf(c.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now()));
                return ps;
            }, keyHolder);
            c.setId(keyHolder.getKey().longValue());
        }
        return c;
    }

    public List<KnowledgeChunk> findBySourceTitleContaining(String keyword) {
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_chunks WHERE source_title LIKE ?",
                rowMapper,
                "%" + keyword + "%"
        );
    }

    public List<KnowledgeChunk> findByContentContaining(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        keyword = keyword.trim();

        // 拆词：关键词本身 + 双字组合 + 单字，用 OR LIKE 搜索
        List<String> terms = new ArrayList<>();
        terms.add(keyword);

        char[] chars = keyword.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            String bigram = new String(new char[]{chars[i], chars[i + 1]});
            if (!terms.contains(bigram)) terms.add(bigram);
        }
        for (char c : chars) {
            String s = String.valueOf(c);
            if (!terms.contains(s)) terms.add(s);
        }

        // 用 CASE WHEN 计算匹配分数：精确匹配 100 分，双字匹配 10 分，单字匹配 1 分
        StringBuilder scoreSql = new StringBuilder();
        StringBuilder whereSql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < terms.size(); i++) {
            String term = "%" + terms.get(i) + "%";
            int weight = (i == 0) ? 100 : (i < 1 + chars.length - 1) ? 10 : 1;

            if (i > 0) {
                scoreSql.append(" + ");
                whereSql.append(" OR ");
            }
            scoreSql.append("(CASE WHEN content LIKE ? THEN ").append(weight).append(" ELSE 0 END)");
            whereSql.append("content LIKE ?");
            params.add(term); // for score
            params.add(term); // for where
        }

        String sql = "SELECT * FROM knowledge_chunks WHERE " + whereSql +
                " ORDER BY (" + scoreSql + ") DESC LIMIT ?";
        params.add(limit);

        return jdbcTemplate.query(sql, rowMapper, params.toArray());
    }

    public void deleteBySourceIdAndSourceType(Long sourceId, String sourceType) {
        jdbcTemplate.update(
                "DELETE FROM knowledge_chunks WHERE source_id = ? AND source_type = ?",
                sourceId, sourceType
        );
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM knowledge_chunks");
    }

    public long count() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunks", Long.class);
        return result != null ? result : 0;
    }

    public List<KnowledgeChunk> findAll() {
        return jdbcTemplate.query("SELECT * FROM knowledge_chunks", rowMapper);
    }

    public int deleteBySourceTitleAndSourceType(String sourceTitle, String sourceType) {
        return jdbcTemplate.update(
            "DELETE FROM knowledge_chunks WHERE source_title = ? AND source_type = ?",
            sourceTitle, sourceType
        );
    }

    public int deleteBySourceTitleLike(String pattern) {
        return jdbcTemplate.update(
            "DELETE FROM knowledge_chunks WHERE source_title LIKE ?",
            pattern
        );
    }

    public List<KnowledgeChunk> findBySourceTitleAndSourceType(String sourceTitle, String sourceType) {
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_chunks WHERE source_title = ? AND source_type = ? ORDER BY chunk_index",
                rowMapper,
                sourceTitle, sourceType
        );
    }

    public List<Map<String, Object>> findDistinctSources() {
        return jdbcTemplate.queryForList(
            "SELECT source_type, source_title, source_id, COUNT(*) as chunk_count, " +
            "GROUP_CONCAT(vector_id) as vector_ids " +
            "FROM knowledge_chunks GROUP BY source_type, source_title, source_id " +
            "ORDER BY MAX(created_at) DESC"
        );
    }
}
