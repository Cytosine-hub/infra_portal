package com.middleware.manager.constant;

/**
 * 错误消息常量（中文）
 */
public final class ErrorMessages {
    private ErrorMessages() {}

    // 通用
    public static final String UNKNOWN_ERROR = "系统异常，请稍后重试";
    public static final String PARAM_INVALID = "参数校验失败";

    // 认证授权
    public static final String UNAUTHORIZED = "未认证，请先登录";
    public static final String FORBIDDEN = "权限不足";
    public static final String TOKEN_EXPIRED = "登录已过期，请重新登录";

    // 资源操作
    public static final String NOT_FOUND = "资源不存在";
    public static final String DUPLICATE = "数据已存在";
    public static final String STATUS_CONFLICT = "当前状态不允许此操作";

    // 论坛标签相关
    public static final String FORUM_TAG_NOT_FOUND = "论坛标签不存在";
    public static final String FORUM_TAG_DUPLICATE = "该小组已存在同名标签";
    public static final String FORUM_TAG_NAME_REQUIRED = "标签名称不能为空";
    public static final String FORUM_TAG_NAME_TOO_LONG = "标签名称不能超过 50 个字符";
    public static final String FORUM_TAG_CATEGORY_REQUIRED = "请选择所属小组";

    // 文件操作
    public static final String FILE_TOO_LARGE = "上传文件超过大小限制";
    public static final String FILE_UPLOAD_FAILED = "文件上传失败";
    public static final String FILE_READ_FAILED = "文件读取失败";
    public static final String KNOWLEDGE_CONTENT_EMPTY = "文档未解析出可索引的正文";
    public static final String DIAGNOSTIC_ATTACHMENT_COUNT_EXCEEDED = "每次最多上传 5 个附件";
    public static final String DIAGNOSTIC_ATTACHMENT_TOO_LARGE = "单个附件不能超过 10MB";
    public static final String DIAGNOSTIC_ATTACHMENT_TOTAL_SIZE_EXCEEDED = "附件总大小不能超过 20MB";
    public static final String DIAGNOSTIC_ATTACHMENT_TYPE_UNSUPPORTED = "不支持该附件格式";
    public static final String DIAGNOSTIC_ATTACHMENT_EMPTY = "附件内容为空";
    public static final String DIAGNOSTIC_ATTACHMENT_READ_FAILED = "附件读取失败";
    public static final String DIAGNOSTIC_ATTACHMENT_PROMPT = "请分析附件内容并给出排查结论";
    public static final String DIAGNOSTIC_MESSAGE_REQUIRED = "消息或附件不能为空";

    // 用户相关
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String USER_DUPLICATE = "账号已存在";
    public static final String PASSWORD_INVALID = "密码错误";
    public static final String PASSWORD_TOO_SHORT = "密码至少6位";
    public static final String ROLE_NOT_FOUND = "未知角色";
    public static final String ROLE_CANNOT_DELETE_SYSTEM = "不能删除系统内置角色";
    public static final String LAST_ADMIN_CANNOT_DELETE = "不能删除最后一个系统管理员";

    // 文档相关
    public static final String DOCUMENT_NOT_FOUND = "文档不存在";
    public static final String DOCUMENT_PUBLISHED = "已发布文档不能编辑，请先下架";
    public static final String DOCUMENT_STATUS_CONFLICT = "当前状态不允许此操作";
    public static final String TITLE_DUPLICATE = "标题已存在";

    // 参数标准相关
    public static final String PARAMETER_STANDARD_NOT_FOUND = "参数标准不存在";
    public static final String PARAMETER_STANDARD_PUBLISHED = "已发布的参数标准不能删除";
    public static final String PARAMETER_STANDARD_STATUS_CONFLICT = "当前状态不可编辑";
    public static final String PARAMETER_STANDARD_UNDER_REVIEW = "该标准已在审核中";
    public static final String PARAMETER_STANDARD_HAS_REFERENCES = "该参数标准已被标准文档或发布资源引用，无法删除";

    // 标准参数相关
    public static final String STANDARD_PARAMETER_NOT_FOUND = "标准参数不存在";
    public static final String PARAMETER_CODE_DUPLICATE = "该标准下参数编码已存在";
    public static final String PARAMETER_BINDING_INVALID = "参数必须绑定标准";
    public static final String PARAMETER_BINDING_ONLY_ONE = "参数只能绑定一种标准类型";
    public static final String PARAMETER_BINDING_DOCUMENT_NOT_FOUND = "绑定的标准不存在";
    public static final String PARAMETER_BINDING_STANDARD_NOT_FOUND = "绑定的参数标准不存在";
    public static final String PARAMETER_NAME_REQUIRED = "标准参数名称不能为空";
    public static final String PARAMETER_VALUE_REQUIRED = "标准参数值不能为空";
    public static final String PARAMETER_CODE_REQUIRED = "标准参数编码不能为空";
    public static final String PARAMETER_TYPE_REQUIRED = "参数类型不能为空";
    public static final String PARAMETER_VALUE_RANGE_REQUIRED = "取值范围不能为空";

    // 审核相关
    public static final String REVIEW_NOT_FOUND = "审核记录不存在";
    public static final String REVIEW_STATUS_CONFLICT = "该审核记录不是待审核状态";

    // 软件类型相关
    public static final String SOFTWARE_TYPE_NOT_FOUND = "软件类型不存在";
    public static final String SOFTWARE_TYPE_DUPLICATE = "该分类下软件类型已存在";
    public static final String SOFTWARE_TYPE_IN_USE = "该软件类型正在使用中，无法删除";
    public static final String SOFTWARE_TYPE_LOOKUP_FAILED = "软件目录服务暂不可用";

    // 发布资源相关
    public static final String RELEASE_NOT_FOUND = "资源不存在";
    public static final String RELEASE_PUBLISHED = "已发布资源不能编辑，请先下架";

    // 中间件命令相关
    public static final String COMMAND_NOT_FOUND = "命令不存在";
    public static final String COMMAND_EXPORT_FAILED = "命令导出失败";
    public static final String COMMAND_IMPORT_FAILED = "命令导入失败";

    // 图片相关
    public static final String IMAGE_FILE_REQUIRED = "请选择图片文件";
    public static final String IMAGE_TYPE_NOT_SUPPORTED = "仅支持图片文件上传";

    // 文档上传相关
    public static final String DOCUMENT_FILE_REQUIRED = "请选择要上传的文件";
    public static final String DOCUMENT_FILE_TOO_LARGE = "文件大小不能超过 20MB";
    public static final String DOCUMENT_FORMAT_NOT_SUPPORTED = "仅支持 .doc、.docx、.md 格式的文件";
    public static final String DOCUMENT_PARSE_FAILED = "文档解析失败";
    public static final String DOCUMENT_CONVERT_FAILED = "文档转换失败";

    // Wiki 导入导出
    public static final String WIKI_IMPORT_SIGNATURE_INVALID = "导入包签名校验失败，请确认导出端和导入端使用相同的 WIKI_EXPORT_SIGNATURE_SECRET，且导入包未被修改";
    public static final String WIKI_INGEST_FAILED = "LLM 编译失败";
    public static final String WIKI_INGEST_TASK_FAILED = "Wiki 编译失败，请稍后重试";
    public static final String WIKI_INGEST_EMPTY_RESULT = "LLM 未生成任何 Wiki 页面";
    public static final String WIKI_SECTION_FACTS_FAILED = "章节事实抽取失败";
    public static final String WIKI_PAGE_PLAN_FAILED = "页面计划生成失败";
    public static final String WIKI_PAGE_GENERATION_FAILED = "页面生成失败";
    public static final String WIKI_QUALITY_GATE_FAILED = "质量门禁失败";
    public static final String WIKI_QUALITY_GATE_PARTIAL = "质量门禁部分通过";
    public static final String WIKI_NO_COMPRESSED_PAGES = "无过度压缩页面";
    public static final String WIKI_NO_MISSING_SECTIONS = "无缺失章节";
    public static final String WIKI_MISSING_ARTIFACTS = "缺少 section_facts 或 page_plan";
    public static final String WIKI_NO_MATCHING_COMPRESSED_PLANS = "page_plan 中未找到匹配的过度压缩页面";
    public static final String WIKI_NO_MATCHING_MISSING_PLANS = "page_plan 中未找到覆盖缺失章节的页面";
    public static final String WIKI_RECOMPILE_FAILED = "重新生成失败";
    public static final String WIKI_RECOMPILE_ERROR = "重编译失败";
    public static final String WIKI_TASK_PAUSED = "任务已暂停";

    // 基础设施
    public static final String SHA256_UNAVAILABLE = "SHA-256 算法不可用";
    public static final String LLM_AUTH_FAILED = "模型服务认证失败，请检查 API Key 配置";
    public static final String LLM_SERVICE_BUSY = "模型服务繁忙，请稍后再试";
    public static final String LLM_RESPONSE_TIMEOUT = "模型响应超时，请稍后再试";
    public static final String LLM_STREAM_UNAVAILABLE = "流式响应不可用，已切换为普通响应";

    // 知识库源文档与起草
    public static final String WIKI_SOURCE_NOT_FOUND = "源文档不存在";
    public static final String WIKI_DRAFT_FAILED = "起草失败，模型未返回有效内容";
    public static final String WIKI_LINK_RESOLVE_FAILED = "页面已保存，但正文中的关联未能建立，图谱与关联检索会缺少这些边。可稍后点击「重建关联」重试。";
}
