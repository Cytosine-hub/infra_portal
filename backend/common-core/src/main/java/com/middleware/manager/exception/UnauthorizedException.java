package com.middleware.manager.exception;

/**
 * 认证失败异常。
 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
