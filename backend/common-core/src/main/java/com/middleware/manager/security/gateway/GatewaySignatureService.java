package com.middleware.manager.security.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class GatewaySignatureService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final byte[] secret;

    public GatewaySignatureService(String secret) {
        byte[] secretBytes = value(secret).getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "GATEWAY_SIGNING_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.secret = secretBytes.clone();
    }

    public String signIntrospectionToken(String token) {
        return sign("introspect\n" + value(token));
    }

    public boolean verifyIntrospectionToken(String token, String signature) {
        return constantTimeEquals(signIntrospectionToken(token), signature);
    }

    public String signIdentityHeaders(String username, String displayName, String roles,
                                      String category, String categoryAdmin) {
        return sign(identityPayload(username, displayName, roles, category, categoryAdmin));
    }

    public boolean verifyIdentityHeaders(String username, String displayName, String roles,
                                         String category, String categoryAdmin, String signature) {
        return constantTimeEquals(
                signIdentityHeaders(username, displayName, roles, category, categoryAdmin),
                signature);
    }

    private String identityPayload(String username, String displayName, String roles,
                                   String category, String categoryAdmin) {
        return "identity\n"
                + "user\n" + value(username) + "\n"
                + "displayName\n" + value(displayName) + "\n"
                + "roles\n" + value(roles) + "\n"
                + "category\n" + value(category) + "\n"
                + "categoryAdmin\n" + value(categoryAdmin);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static String value(String input) {
        return input == null ? "" : input;
    }
}
