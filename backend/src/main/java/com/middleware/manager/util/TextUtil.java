package com.middleware.manager.util;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文本处理工具类，消除各 Service 中的重复方法。
 */
public final class TextUtil {

    private TextUtil() {}

    /**
     * 去空白后返回 null（空字符串也返回 null）。
     */
    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 校验非空并去空白，否则抛出 BusinessException。
     */
    public static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    /**
     * SHA-256 哈希后返回十六进制字符串。
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, ErrorMessages.SHA256_UNAVAILABLE);
        }
    }
}
