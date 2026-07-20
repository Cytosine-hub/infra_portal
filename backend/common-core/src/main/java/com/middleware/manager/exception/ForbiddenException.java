package com.middleware.manager.exception;

/**
 * 权限不足异常
 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
