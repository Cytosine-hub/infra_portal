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

    // 文件操作
    public static final String FILE_TOO_LARGE = "上传文件超过大小限制";
    public static final String FILE_UPLOAD_FAILED = "文件上传失败";

    // 用户相关
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String USER_DUPLICATE = "账号已存在";
    public static final String PASSWORD_INVALID = "密码错误";
    public static final String PASSWORD_TOO_SHORT = "密码至少6位";
    public static final String ROLE_NOT_FOUND = "未知角色";
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

    // 标准参数相关
    public static final String STANDARD_PARAMETER_NOT_FOUND = "标准参数不存在";
    public static final String PARAMETER_CODE_DUPLICATE = "该标准下参数编码已存在";
    public static final String PARAMETER_BINDING_INVALID = "参数必须绑定标准";

    // 审核相关
    public static final String REVIEW_NOT_FOUND = "审核记录不存在";
    public static final String REVIEW_STATUS_CONFLICT = "该审核记录不是待审核状态";

    // 软件类型相关
    public static final String SOFTWARE_TYPE_NOT_FOUND = "软件类型不存在";
    public static final String SOFTWARE_TYPE_DUPLICATE = "该分类下软件类型已存在";
    public static final String SOFTWARE_TYPE_IN_USE = "该软件类型正在使用中，无法删除";

    // 发布资源相关
    public static final String RELEASE_NOT_FOUND = "资源不存在";
    public static final String RELEASE_PUBLISHED = "已发布资源不能编辑，请先下架";

    // 基础设施
    public static final String SHA256_UNAVAILABLE = "SHA-256 算法不可用";
}
