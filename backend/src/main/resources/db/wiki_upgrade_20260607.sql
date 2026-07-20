-- LLM Wiki production-hardening upgrade.
-- Run this once on databases created before 2026-06-07.

ALTER TABLE wiki_ingest_tasks
    MODIFY status ENUM('PENDING','PROCESSING','COMPLETED','PARTIAL','FAILED') DEFAULT 'PENDING';

ALTER TABLE wiki_lint_results
    ADD COLUMN fingerprint VARCHAR(128) NULL AFTER page_id,
    ADD COLUMN first_seen_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP AFTER resolved_at,
    ADD COLUMN last_seen_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP AFTER first_seen_at,
    ADD COLUMN ignored_until TIMESTAMP NULL AFTER last_seen_at;

UPDATE wiki_lint_results
SET fingerprint = CONCAT(lint_type, ':legacy:', id),
    first_seen_at = COALESCE(first_seen_at, created_at, NOW()),
    last_seen_at = COALESCE(last_seen_at, created_at, NOW())
WHERE fingerprint IS NULL;

ALTER TABLE wiki_lint_results
    MODIFY fingerprint VARCHAR(128) NOT NULL,
    ADD UNIQUE KEY uk_lint_fingerprint (fingerprint);
