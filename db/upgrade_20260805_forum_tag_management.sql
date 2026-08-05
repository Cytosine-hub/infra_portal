ALTER TABLE forum_tags
    DROP INDEX name,
    ADD COLUMN category VARCHAR(100) NULL AFTER post_count,
    ADD COLUMN created_by VARCHAR(100) NULL AFTER category,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD UNIQUE KEY uk_forum_tag_category_name (category, name),
    ADD KEY idx_forum_tag_category (category);
