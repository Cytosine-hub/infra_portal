package com.middleware.manager.exception;

/**
 * 资源未找到异常
 */
public class NotFoundException extends BusinessException {
    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
