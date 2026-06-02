package com.middleware.manager.service;

import com.middleware.manager.repository.UserTokenMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);
    private static final long TOKEN_EXPIRY_HOURS = 2;

    private final UserTokenMapper tokenMapper;

    public TokenService(UserTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    public String createToken(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);

        tokenMapper.insert(token, username, expiresAt);

        LOGGER.info("token created for user={} expiresAt={}", username, expiresAt);
        return token;
    }

    public String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // 清理过期 token
        tokenMapper.deleteExpired();

        String username = tokenMapper.findUsernameByToken(token);

        if (username != null) {
            // 滑动续期
            LocalDateTime newExpiry = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);
            tokenMapper.updateExpiry(token, newExpiry);
        }

        return username;
    }

    public void deleteToken(String token) {
        if (token != null) {
            int deleted = tokenMapper.deleteByToken(token);
            LOGGER.info("token deleted rows={}", deleted);
        }
    }

    public void deleteAllTokensForUser(String username) {
        tokenMapper.deleteByUsername(username);
        LOGGER.info("all tokens deleted for user={}", username);
    }
}
