package com.middleware.manager.security;

public interface TokenValidator {
    String validateToken(String token);
}
