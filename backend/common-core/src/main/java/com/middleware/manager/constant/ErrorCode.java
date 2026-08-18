package com.middleware.manager.constant;

/**
 * 错误码常量
 */
public final class ErrorCode {
    private ErrorCode() {}

    // 通用错误
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String PARAM_INVALID = "PARAM_INVALID";

    // 认证授权
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";

    // 资源操作
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String STATUS_CONFLICT = "STATUS_CONFLICT";

    // 论坛标签相关
    public static final String FORUM_TAG_NOT_FOUND = "FORUM_TAG_NOT_FOUND";
    public static final String FORUM_TAG_DUPLICATE = "FORUM_TAG_DUPLICATE";

    // 文件操作
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String FILE_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";
    public static final String FILE_READ_FAILED = "FILE_READ_FAILED";
    public static final String KNOWLEDGE_CONTENT_EMPTY = "KNOWLEDGE_CONTENT_EMPTY";
    public static final String DIAGNOSTIC_ATTACHMENT_COUNT_EXCEEDED = "DIAGNOSTIC_ATTACHMENT_COUNT_EXCEEDED";
    public static final String DIAGNOSTIC_ATTACHMENT_TOO_LARGE = "DIAGNOSTIC_ATTACHMENT_TOO_LARGE";
    public static final String DIAGNOSTIC_ATTACHMENT_TOTAL_SIZE_EXCEEDED = "DIAGNOSTIC_ATTACHMENT_TOTAL_SIZE_EXCEEDED";
    public static final String DIAGNOSTIC_ATTACHMENT_TYPE_UNSUPPORTED = "DIAGNOSTIC_ATTACHMENT_TYPE_UNSUPPORTED";
    public static final String DIAGNOSTIC_ATTACHMENT_EMPTY = "DIAGNOSTIC_ATTACHMENT_EMPTY";
    public static final String DIAGNOSTIC_ATTACHMENT_READ_FAILED = "DIAGNOSTIC_ATTACHMENT_READ_FAILED";

    // 用户相关
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_DUPLICATE = "USER_DUPLICATE";
    public static final String PASSWORD_INVALID = "PASSWORD_INVALID";
    public static final String PASSWORD_TOO_SHORT = "PASSWORD_TOO_SHORT";
    public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
    public static final String LAST_ADMIN_CANNOT_DELETE = "LAST_ADMIN_CANNOT_DELETE";

    // 文档相关
    public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
    public static final String DOCUMENT_PUBLISHED = "DOCUMENT_PUBLISHED";
    public static final String DOCUMENT_STATUS_CONFLICT = "DOCUMENT_STATUS_CONFLICT";
    public static final String TITLE_DUPLICATE = "TITLE_DUPLICATE";

    // 参数标准相关
    public static final String PARAMETER_STANDARD_NOT_FOUND = "PARAMETER_STANDARD_NOT_FOUND";
    public static final String PARAMETER_STANDARD_PUBLISHED = "PARAMETER_STANDARD_PUBLISHED";
    public static final String PARAMETER_STANDARD_STATUS_CONFLICT = "PARAMETER_STANDARD_STATUS_CONFLICT";
    public static final String PARAMETER_STANDARD_UNDER_REVIEW = "PARAMETER_STANDARD_UNDER_REVIEW";
    public static final String PARAMETER_STANDARD_HAS_REFERENCES = "PARAMETER_STANDARD_HAS_REFERENCES";

    // 标准参数相关
    public static final String STANDARD_PARAMETER_NOT_FOUND = "STANDARD_PARAMETER_NOT_FOUND";
    public static final String PARAMETER_CODE_DUPLICATE = "PARAMETER_CODE_DUPLICATE";
    public static final String PARAMETER_BINDING_INVALID = "PARAMETER_BINDING_INVALID";

    // 审核相关
    public static final String REVIEW_NOT_FOUND = "REVIEW_NOT_FOUND";
    public static final String REVIEW_STATUS_CONFLICT = "REVIEW_STATUS_CONFLICT";

    // 软件类型相关
    public static final String SOFTWARE_TYPE_NOT_FOUND = "SOFTWARE_TYPE_NOT_FOUND";
    public static final String SOFTWARE_TYPE_DUPLICATE = "SOFTWARE_TYPE_DUPLICATE";
    public static final String SOFTWARE_TYPE_IN_USE = "SOFTWARE_TYPE_IN_USE";
    public static final String SOFTWARE_TYPE_LOOKUP_FAILED = "SOFTWARE_TYPE_LOOKUP_FAILED";

    // 中间件命令相关
    public static final String COMMAND_NOT_FOUND = "COMMAND_NOT_FOUND";
    public static final String COMMAND_EXPORT_FAILED = "COMMAND_EXPORT_FAILED";
    public static final String COMMAND_IMPORT_FAILED = "COMMAND_IMPORT_FAILED";

    // 发布资源相关
    public static final String RELEASE_NOT_FOUND = "RELEASE_NOT_FOUND";
    public static final String RELEASE_PUBLISHED = "RELEASE_PUBLISHED";

    // 知识库源文档与起草
    public static final String WIKI_SOURCE_NOT_FOUND = "WIKI_SOURCE_NOT_FOUND";
    public static final String WIKI_DRAFT_FAILED = "WIKI_DRAFT_FAILED";
    public static final String WIKI_LINK_RESOLVE_FAILED = "WIKI_LINK_RESOLVE_FAILED";
}
