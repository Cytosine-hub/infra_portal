CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL COMMENT '切片文本内容',
    source_title VARCHAR(500) COMMENT '来源文档标题',
    source_type VARCHAR(50) COMMENT '来源类型：STANDARD_DOC / UPLOAD',
    source_id BIGINT COMMENT '来源文档ID',
    category VARCHAR(80) COMMENT '分类',
    software VARCHAR(120) COMMENT '软件名称',
    chunk_index INT DEFAULT 0 COMMENT '切片在文档中的序号',
    vector_id VARCHAR(100) COMMENT '向量存储ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source (source_type, source_id),
    INDEX idx_category (category),
    INDEX idx_software (software)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文本切片';

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) COMMENT '会话标题',
    mode VARCHAR(10) DEFAULT 'rag' COMMENT '会话模式：rag / ops',
    created_by BIGINT COMMENT '创建人用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_chat_sessions_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话';

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user / assistant / system',
    content TEXT NOT NULL COMMENT '消息内容',
    references_text TEXT COMMENT '引用的知识来源JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息';

CREATE TABLE IF NOT EXISTS agent_tool_invocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT COMMENT '排查会话ID',
    step_name VARCHAR(200) COMMENT '步骤名称',
    tool_name VARCHAR(120) NOT NULL COMMENT '工具名称',
    request_json TEXT COMMENT '脱敏后的请求参数JSON',
    response_json MEDIUMTEXT COMMENT '脱敏后的工具响应JSON',
    status VARCHAR(30) NOT NULL COMMENT 'SUCCESS / FAILED / TIMEOUT / FORBIDDEN',
    error_code VARCHAR(80) COMMENT '错误码',
    error_message VARCHAR(1000) COMMENT '错误摘要',
    latency_ms BIGINT COMMENT '工具调用耗时',
    created_by BIGINT COMMENT '调用人用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_tool_invocations_session (session_id),
    INDEX idx_agent_tool_invocations_tool (tool_name),
    INDEX idx_agent_tool_invocations_created_by (created_by),
    INDEX idx_agent_tool_invocations_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具调用审计';

-- 参数标准表
CREATE TABLE IF NOT EXISTS parameter_standards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(160) NOT NULL COMMENT '标准标题',
    category VARCHAR(60) COMMENT '分类',
    software VARCHAR(120) COMMENT '软件名称',
    software_type_id BIGINT COMMENT '软件类型ID',
    software_version VARCHAR(80) COMMENT '软件版本',
    standard_version VARCHAR(80) COMMENT '标准版本',
    code VARCHAR(20) COMMENT '编码',
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    content TEXT NOT NULL COMMENT '内容（Markdown）',
    rendered_content TEXT COMMENT '渲染后内容',
    published_at DATETIME COMMENT '发布时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数标准';

-- 数据迁移：将 STANDARD 类型文档迁移到新表
INSERT IGNORE INTO parameter_standards (id, title, category, software, software_type_id, software_version, standard_version, code, status, content, rendered_content, published_at, created_at, updated_at)
SELECT id, title, category, software, software_type_id, software_version, standard_version, code, status, content, rendered_content, published_at, created_at, updated_at
FROM standard_documents WHERE document_type = 'STANDARD';

-- 更新 StandardParameter 关联
ALTER TABLE standard_parameters ADD COLUMN IF NOT EXISTS parameter_standard_id BIGINT COMMENT '关联的参数标准ID';
UPDATE standard_parameters SET parameter_standard_id = standard_document_id WHERE parameter_standard_id IS NULL AND standard_document_id IN (SELECT id FROM parameter_standards);

-- 删除旧的 STANDARD 类型文档
DELETE FROM standard_documents WHERE document_type = 'STANDARD';

-- 参数标准添加审核支持（列不存在时执行）
-- ALTER TABLE parameter_standards ADD COLUMN pending_review_record_id BIGINT COMMENT '待审核记录ID';
-- ALTER TABLE parameter_standards ADD COLUMN previous_content TEXT COMMENT '修改前内容备份';
-- ALTER TABLE parameter_standards ADD COLUMN version VARCHAR(20) COMMENT '版本号';

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色显示名称',
    authority VARCHAR(80) NOT NULL UNIQUE COMMENT 'Spring Security authority',
    managed_category VARCHAR(60) COMMENT '管理的分类',
    category_admin BOOLEAN NOT NULL DEFAULT false COMMENT '是否为专业管理员（有审批权）',
    system_role BOOLEAN NOT NULL DEFAULT false COMMENT '是否为系统内置角色',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY COMMENT '设置键',
    setting_value VARCHAR(500) NOT NULL COMMENT '设置值',
    description VARCHAR(200) COMMENT '说明',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置';

INSERT IGNORE INTO system_settings (setting_key, setting_value, description) VALUES
('knowledge-enabled', 'true', '知识库模块开关'),
('diagnostics-enabled', 'true', '智能排查模块开关');

-- 用户登录令牌表
CREATE TABLE IF NOT EXISTS user_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(120) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录令牌';

-- 标准包相关字段（release_assets 表扩展，如已存在会报 Duplicate column 可忽略）
ALTER TABLE release_assets ADD COLUMN standard_package BOOLEAN NOT NULL DEFAULT false COMMENT '是否为标准包';
ALTER TABLE release_assets ADD COLUMN parameter_standard_id BIGINT COMMENT '关联的参数标准ID';
ALTER TABLE release_assets ADD COLUMN package_status VARCHAR(20) DEFAULT NULL COMMENT '标准包状态: PENDING/PROCESSING/SUCCESS/FAILED';
ALTER TABLE release_assets ADD COLUMN package_error TEXT DEFAULT NULL COMMENT '标准包生成错误信息';

-- 文档修订记录表
CREATE TABLE IF NOT EXISTS document_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '文档ID',
    document_type VARCHAR(40) NOT NULL COMMENT 'PARAMETER_STANDARD 或文档类型(MANUAL/ARTICLE)',
    version VARCHAR(20) NOT NULL COMMENT '发布版本号',
    content TEXT COMMENT '修订时的完整内容',
    rendered_content TEXT COMMENT '渲染后内容',
    revision_comment VARCHAR(1000) COMMENT '修订说明/审核意见',
    revised_by VARCHAR(80) COMMENT '修订人（审核人）',
    submitted_by VARCHAR(80) COMMENT '提交人',
    revised_at DATETIME NOT NULL COMMENT '修订（发布）时间',
    category VARCHAR(60),
    software VARCHAR(120),
    INDEX idx_doc (document_id, document_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档修订记录';
