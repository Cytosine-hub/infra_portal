SET NAMES utf8mb4;

DROP TABLE IF EXISTS `admin_accounts`;
CREATE TABLE `admin_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `username` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4pq3nsva3pn231gh89pnqdey1` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP TABLE IF EXISTS `agent_tool_invocations`;
CREATE TABLE `agent_tool_invocations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint DEFAULT NULL COMMENT '排查会话ID',
  `step_name` varchar(200) DEFAULT NULL COMMENT '步骤名称',
  `tool_name` varchar(120) NOT NULL COMMENT '工具名称',
  `request_json` text COMMENT '脱敏后的请求参数JSON',
  `response_json` mediumtext COMMENT '脱敏后的工具响应JSON',
  `status` varchar(30) NOT NULL COMMENT 'SUCCESS / FAILED / TIMEOUT / FORBIDDEN',
  `error_code` varchar(80) DEFAULT NULL COMMENT '错误码',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误摘要',
  `latency_ms` bigint DEFAULT NULL COMMENT '工具调用耗时',
  `created_by` bigint DEFAULT NULL COMMENT '调用人用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`),
  KEY `idx_tool` (`tool_name`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent工具调用审计';
DROP TABLE IF EXISTS `api_audit_log`;
CREATE TABLE `api_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(60) NOT NULL COMMENT '用户账号',
  `method` varchar(10) NOT NULL COMMENT 'HTTP方法',
  `path` varchar(500) NOT NULL COMMENT '请求路径',
  `query_string` varchar(500) DEFAULT NULL COMMENT '查询参数',
  `status_code` int DEFAULT NULL COMMENT '响应状态码',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `duration_ms` bigint DEFAULT NULL COMMENT '请求耗时(毫秒)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API操作审计日志';
DROP TABLE IF EXISTS `chat_messages`;
CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(20) NOT NULL COMMENT '角色：user / assistant / system',
  `content` text NOT NULL COMMENT '消息内容',
  `references_text` text COMMENT '引用的知识来源JSON',
  `attachments_text` text COMMENT '附件元数据JSON，不包含文件正文',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话消息';
DROP TABLE IF EXISTS `chat_sessions`;
CREATE TABLE `chat_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
  `mode` varchar(10) DEFAULT 'rag' COMMENT '会话模式：rag / ops',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_sessions_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话会话';
DROP TABLE IF EXISTS `document_revisions`;
CREATE TABLE `document_revisions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `document_type` varchar(30) NOT NULL COMMENT '文档类型：STANDARD_DOC/PARAMETER_STANDARD',
  `category` varchar(100) DEFAULT NULL,
  `software` varchar(200) DEFAULT NULL,
  `version` varchar(50) DEFAULT NULL,
  `content` longtext COMMENT '修订内容',
  `rendered_content` longtext,
  `revision_note` varchar(500) DEFAULT NULL COMMENT '修订说明',
  `revision_comment` text,
  `created_by` bigint DEFAULT NULL COMMENT '修订人',
  `revised_at` datetime DEFAULT NULL,
  `revised_by` varchar(100) DEFAULT NULL,
  `submitted_by` varchar(100) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_document` (`document_type`,`document_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档修订历史';
DROP TABLE IF EXISTS `forum_comments`;
CREATE TABLE `forum_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `content` text NOT NULL,
  `author_username` varchar(100) DEFAULT NULL,
  `author_display_name` varchar(100) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `like_count` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_post_likes`;
CREATE TABLE `forum_post_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `username` varchar(100) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`,`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_post_tags`;
CREATE TABLE `forum_post_tags` (
  `post_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`post_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_posts`;
CREATE TABLE `forum_posts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(300) NOT NULL,
  `content` longtext,
  `author_username` varchar(100) DEFAULT NULL,
  `author_display_name` varchar(100) DEFAULT NULL,
  `status` varchar(30) DEFAULT 'PUBLISHED',
  `view_count` int DEFAULT '0',
  `like_count` int DEFAULT '0',
  `comment_count` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `published_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_author` (`author_username`),
  FULLTEXT KEY `ft_forum_posts_title_content` (`title`,`content`) /*!50100 WITH PARSER `ngram` */
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `forum_tags`;
CREATE TABLE `forum_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `post_count` int DEFAULT '0',
  `category` varchar(100) NOT NULL,
  `created_by` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forum_tag_name` (`name`),
  KEY `idx_forum_tag_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;DROP TABLE IF EXISTS `middleware_commands`;
CREATE TABLE `middleware_commands` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `software_type_id` bigint NOT NULL,
  `name` varchar(200) NOT NULL,
  `command` text NOT NULL,
  `brief_description` text,
  `categories` varchar(200) DEFAULT NULL,
  `command_format` text,
  `detailed_description` text,
  `description` text,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_software_type` (`software_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `parameter_standards`;
CREATE TABLE `parameter_standards` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(160) NOT NULL COMMENT '标准标题',
  `category` varchar(60) DEFAULT NULL COMMENT '分类',
  `software` varchar(100) DEFAULT NULL COMMENT '软件名称',
  `software_type_id` bigint DEFAULT NULL,
  `software_version` varchar(50) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `version` varchar(50) DEFAULT NULL COMMENT '版本',
  `status` varchar(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING/APPROVED/PUBLISHED/REJECTED',
  `published_at` datetime DEFAULT NULL,
  `pending_review_record_id` bigint DEFAULT NULL,
  `description` text COMMENT '描述',
  `content` longtext,
  `rendered_content` longtext,
  `previous_content` longtext,
  `previous_rendered_content` longtext,
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='参数标准';
DROP TABLE IF EXISTS `release_assets`;
CREATE TABLE `release_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `software_type_id` bigint NOT NULL,
  `parameter_standard_id` bigint DEFAULT NULL,
  `standard_document_id` bigint DEFAULT NULL,
  `standard_package` tinyint(1) DEFAULT '0',
  `middleware_name` varchar(200) DEFAULT NULL,
  `version` varchar(100) DEFAULT NULL,
  `platform` varchar(100) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `file_name` varchar(300) DEFAULT NULL,
  `original_file_name` varchar(300) DEFAULT NULL,
  `stored_file_name` varchar(300) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `description` text,
  `package_error` text,
  `package_status` varchar(30) DEFAULT NULL,
  `download_token` varchar(100) DEFAULT NULL,
  `published` tinyint(1) DEFAULT '0',
  `download_count` int DEFAULT '0',
  `published_at` datetime DEFAULT NULL,
  `released_at` datetime DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_software_type` (`software_type_id`),
  KEY `idx_published` (`published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `review_records`;
CREATE TABLE `review_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(100) DEFAULT NULL,
  `status` varchar(30) DEFAULT 'PENDING',
  `submitter_username` varchar(100) DEFAULT NULL,
  `submitter_display_name` varchar(100) DEFAULT NULL,
  `reviewer_username` varchar(100) DEFAULT NULL,
  `comment` text,
  `review_comment` text,
  `current_content` longtext,
  `document_id` bigint DEFAULT NULL,
  `document_title` varchar(300) DEFAULT NULL,
  `document_type` varchar(30) DEFAULT NULL,
  `document_version` varchar(50) DEFAULT NULL,
  `previous_content` longtext,
  `software` varchar(200) DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `display_name` varchar(100) NOT NULL,
  `authority` varchar(100) NOT NULL,
  `managed_category` varchar(100) DEFAULT NULL,
  `category_admin` tinyint(1) DEFAULT '0',
  `system_role` tinyint(1) DEFAULT '0',
  `is_category_admin` tinyint(1) DEFAULT '0',
  `is_management` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `authority` (`authority`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `software_categories`;
CREATE TABLE `software_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `software_types`;
CREATE TABLE `software_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(100) NOT NULL,
  `name` varchar(200) NOT NULL,
  `description` text,
  `active` tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`category`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `standard_documents`;
CREATE TABLE `standard_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(300) NOT NULL,
  `document_type` varchar(30) NOT NULL,
  `category` varchar(100) DEFAULT NULL,
  `software` varchar(200) DEFAULT NULL,
  `software_version` varchar(50) DEFAULT NULL,
  `standard_version` varchar(50) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `status` varchar(30) DEFAULT 'DRAFT',
  `pending_review_record_id` bigint DEFAULT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `content` longtext,
  `rendered_content` longtext,
  `previous_content` longtext,
  `stored_file_name` varchar(255) DEFAULT NULL COMMENT '存储的文件名',
  `original_file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `review_comment` text,
  `submitted_at` datetime DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `reviewed_by` varchar(100) DEFAULT NULL,
  `related_standard_document_id` bigint DEFAULT NULL,
  `software_type_id` bigint DEFAULT NULL,
  `parameter_standard_id` bigint DEFAULT NULL,
  `version` varchar(50) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`document_type`,`status`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `standard_parameters`;
CREATE TABLE `standard_parameters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(100) NOT NULL,
  `name` varchar(200) DEFAULT NULL,
  `param_type` varchar(100) DEFAULT NULL COMMENT '参数类型（文本值/布尔值/数值）',
  `value_range` varchar(200) DEFAULT NULL COMMENT '取值范围',
  `value` text,
  `description` text,
  `deployment_standard` varchar(200) DEFAULT NULL,
  `standard_document_id` bigint DEFAULT NULL,
  `parameter_standard_id` bigint DEFAULT NULL,
  `active` tinyint(1) DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`),
  KEY `idx_document` (`standard_document_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `system_settings`;
CREATE TABLE `system_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(100) NOT NULL COMMENT '设置键',
  `setting_value` text COMMENT '设置值',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统设置';
DROP TABLE IF EXISTS `user_tokens`;
CREATE TABLE `user_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(500) NOT NULL,
  `username` varchar(100) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_token` (`token`(100)),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `wiki_access_requests`;
CREATE TABLE `wiki_access_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `page_id` bigint NOT NULL,
  `requester_id` bigint NOT NULL,
  `status` enum('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
  `reason` text,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_page` (`page_id`),
  KEY `idx_requester` (`requester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `wiki_audit_log`;
CREATE TABLE `wiki_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('PAGE_VIEW','PAGE_CREATE','PAGE_EDIT','PAGE_DELETE','PAGE_SUBMIT','PAGE_APPROVE','PAGE_REJECT','INGEST_RUN','INGEST_EXPORT','INGEST_IMPORT','LINT_RUN','LINT_RESOLVE','PERMISSION_CHANGE','ACCESS_DENIED') NOT NULL,
  `target_type` enum('PAGE','SOURCE','LINK','PERMISSION','SYSTEM') NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `actor_role` varchar(50) DEFAULT NULL,
  `actor_ip` varchar(50) DEFAULT NULL,
  `detail` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_actor` (`actor_id`),
  KEY `idx_action_time` (`action`,`created_at`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;DROP TABLE IF EXISTS `wiki_lint_results`;
CREATE TABLE `wiki_lint_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lint_type` enum('ORPHAN','STALE','BROKEN_LINK','CONTRADICTION','GAP') NOT NULL,
  `page_id` bigint DEFAULT NULL,
  `fingerprint` varchar(128) NOT NULL,
  `description` text NOT NULL,
  `severity` enum('LOW','MEDIUM','HIGH') DEFAULT 'MEDIUM',
  `resolved` tinyint(1) DEFAULT '0',
  `resolved_by` bigint DEFAULT NULL,
  `resolved_at` timestamp NULL DEFAULT NULL,
  `first_seen_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_seen_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `ignored_until` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lint_fingerprint` (`fingerprint`),
  KEY `idx_unresolved` (`resolved`,`severity`),
  KEY `idx_page` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `wiki_page_permissions`;
CREATE TABLE `wiki_page_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `page_id` bigint NOT NULL,
  `permission_type` enum('VISIBLE','RESTRICTED','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  `target_roles` json DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `wiki_pages`;
CREATE TABLE `wiki_pages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `page_type` enum('ENTITY','CONCEPT','RUNBOOK','EXPERIENCE','STANDARD','SYNTHESIS','OVERVIEW') NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `software` varchar(100) DEFAULT NULL,
  `version` varchar(50) DEFAULT NULL,
  `canonical_title` varchar(200) DEFAULT NULL,
  `alias_titles` json DEFAULT NULL,
  `content` longtext NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `source_refs` json DEFAULT NULL,
  `status` enum('DRAFT','PENDING_REVIEW','ACTIVE','STALE','CONTRADICTED','REJECTED') DEFAULT 'ACTIVE',
  `contradiction_note` text,
  `compiled_by` varchar(100) DEFAULT NULL,
  `compiled_at` timestamp NULL DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_title_type` (`title`,`page_type`),
  KEY `idx_category_software` (`category`,`software`),
  KEY `idx_status` (`status`),
  KEY `idx_software_version` (`software`,`version`),
  KEY `idx_canonical_title` (`canonical_title`),
  FULLTEXT KEY `ft_content` (`title`,`summary`,`content`) /*!50700 WITH PARSER `ngram` */
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
DROP TABLE IF EXISTS `wiki_sources`;
CREATE TABLE `wiki_sources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `source_type` varchar(40) NOT NULL,
  `source_ref` varchar(100) DEFAULT NULL,
  `file_path` varchar(500) DEFAULT NULL,
  `content_hash` varchar(64) DEFAULT NULL,
  `content` longtext,
  `category` varchar(50) DEFAULT NULL,
  `software` varchar(100) DEFAULT NULL,
  `ingested` tinyint(1) DEFAULT '0',
  `ingested_at` timestamp NULL DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ingested` (`ingested`),
  KEY `idx_content_hash` (`content_hash`),
  UNIQUE KEY `uk_source_ref` (`source_type`,`source_ref`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
