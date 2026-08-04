-- 开放知识来源类型，并用业务来源 ID 保证重复导入时更新原记录。
ALTER TABLE wiki_sources
    MODIFY COLUMN source_type VARCHAR(40) NOT NULL;

SET @source_ref_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'wiki_sources'
      AND column_name = 'source_ref'
);
SET @source_ref_column_sql = IF(
    @source_ref_column_exists = 0,
    'ALTER TABLE wiki_sources ADD COLUMN source_ref VARCHAR(100) NULL AFTER source_type',
    'SELECT 1'
);
PREPARE source_ref_column_stmt FROM @source_ref_column_sql;
EXECUTE source_ref_column_stmt;
DEALLOCATE PREPARE source_ref_column_stmt;

SET @source_ref_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'wiki_sources'
      AND index_name = 'uk_source_ref'
);
SET @source_ref_index_sql = IF(
    @source_ref_index_exists = 0,
    'ALTER TABLE wiki_sources ADD UNIQUE KEY uk_source_ref (source_type, source_ref)',
    'SELECT 1'
);
PREPARE source_ref_index_stmt FROM @source_ref_index_sql;
EXECUTE source_ref_index_stmt;
DEALLOCATE PREPARE source_ref_index_stmt;
