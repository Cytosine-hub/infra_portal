-- ============================================================
-- LLM Wiki 数据模型（无外键，应用层保证数据一致性）
-- ============================================================

-- Wiki 页面：LLM 编译后的结构化知识
CREATE TABLE IF NOT EXISTS wiki_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    page_type ENUM('ENTITY','CONCEPT','RUNBOOK','EXPERIENCE','STANDARD','SYNTHESIS','OVERVIEW') NOT NULL,
    category VARCHAR(50),
    software VARCHAR(100),
    version VARCHAR(50),
    canonical_title VARCHAR(200),
    alias_titles JSON,
    content LONGTEXT NOT NULL,
    summary VARCHAR(500),
    source_refs JSON,
    status ENUM('DRAFT','PENDING_REVIEW','ACTIVE','STALE','CONTRADICTED','REJECTED') DEFAULT 'ACTIVE',
    contradiction_note TEXT,
    compiled_by VARCHAR(100),
    compiled_at TIMESTAMP NULL,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_title_type (title, page_type),
    INDEX idx_category_software (category, software),
    INDEX idx_status (status),
    INDEX idx_software_version (software, version),
    INDEX idx_canonical_title (canonical_title),
    FULLTEXT INDEX ft_content (title, summary, content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 页面间关系（知识图谱的边）
CREATE TABLE IF NOT EXISTS wiki_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_page_id BIGINT NOT NULL,
    to_page_id BIGINT NOT NULL,
    link_type ENUM('REFERENCES','CONTRADICTS','SPECIALIZES','DEPENDS_ON','RELATED') DEFAULT 'REFERENCES',
    confidence DECIMAL(3,2),
    context VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_link (from_page_id, to_page_id, link_type),
    INDEX idx_to_page (to_page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 原始文档（不可变，Raw Sources 层）
CREATE TABLE IF NOT EXISTS wiki_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_ref VARCHAR(100),
    file_path VARCHAR(500),
    content_hash VARCHAR(64),
    content LONGTEXT,
    category VARCHAR(50),
    software VARCHAR(100),
    ingested BOOLEAN DEFAULT FALSE,
    ingested_at TIMESTAMP NULL,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ingested (ingested),
    INDEX idx_content_hash (content_hash),
    UNIQUE KEY uk_source_ref (source_type, source_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Lint 检查结果
CREATE TABLE IF NOT EXISTS wiki_lint_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lint_type ENUM('ORPHAN','STALE','BROKEN_LINK','CONTRADICTION','GAP') NOT NULL,
    page_id BIGINT,
    fingerprint VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    severity ENUM('LOW','MEDIUM','HIGH') DEFAULT 'MEDIUM',
    resolved BOOLEAN DEFAULT FALSE,
    resolved_by BIGINT,
    resolved_at TIMESTAMP NULL,
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ignored_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lint_fingerprint (fingerprint),
    INDEX idx_unresolved (resolved, severity),
    INDEX idx_page (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作审计日志
CREATE TABLE IF NOT EXISTS wiki_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action ENUM(
        'PAGE_VIEW','PAGE_CREATE','PAGE_EDIT','PAGE_DELETE',
        'PAGE_SUBMIT','PAGE_APPROVE','PAGE_REJECT',
        'INGEST_RUN','INGEST_EXPORT','INGEST_IMPORT',
        'LINT_RUN','LINT_RESOLVE',
        'PERMISSION_CHANGE','ACCESS_DENIED'
    ) NOT NULL,
    target_type ENUM('PAGE','SOURCE','LINK','PERMISSION','SYSTEM') NOT NULL,
    target_id BIGINT,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(50),
    actor_ip VARCHAR(50),
    detail JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_actor (actor_id),
    INDEX idx_action_time (action, created_at),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 页面级权限覆盖
CREATE TABLE IF NOT EXISTS wiki_page_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id BIGINT NOT NULL,
    permission_type ENUM('VISIBLE','RESTRICTED','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
    target_roles JSON,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_page_id (page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 访问申请
CREATE TABLE IF NOT EXISTS wiki_access_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    page_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    status ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
    reason TEXT,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_page (page_id),
    INDEX idx_requester (requester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
