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
