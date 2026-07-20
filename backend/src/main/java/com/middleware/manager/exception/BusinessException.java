package com.middleware.manager.exception;

/**
 * 业务异常（用户可理解的错误）
 */
public class BusinessException extends RuntimeException {
    private final String code;
    private final String message;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String code, String message, String detail) {
        super(message + ": " + detail);
        this.code = code;
        this.message = message + ": " + detail;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
