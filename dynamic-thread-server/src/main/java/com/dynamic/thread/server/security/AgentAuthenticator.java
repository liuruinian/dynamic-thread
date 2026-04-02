package com.dynamic.thread.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent authentication handler.
 * Validates agent connections using HMAC-SHA256 signatures.
 */
@Slf4j
@Component
public class AgentAuthenticator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long TOKEN_VALIDITY_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Secret key for HMAC signing (should be configured externally)
     */
    private String secretKey = "dynamic-thread-default-secret-key-change-in-production";

    /**
     * Nonce cache to prevent replay attacks
     */
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

    /**
     * Set the secret key for authentication
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Validate agent authentication.
     *
     * @param appId      Application ID
     * @param instanceId Instance ID
     * @param timestamp  Request timestamp (milliseconds)
     * @param nonce      Random nonce to prevent replay
     * @param signature  HMAC-SHA256 signature
     * @return true if authentication succeeds
     */
    public boolean authenticate(String appId, String instanceId, long timestamp, String nonce, String signature) {
        // 1. Check timestamp validity (prevent replay attacks with old tokens)
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TOKEN_VALIDITY_MS) {
            log.warn("Authentication failed: timestamp expired. appId={}, instanceId={}, diff={}ms",
                    appId, instanceId, now - timestamp);
            return false;
        }

        // 2. Check nonce (prevent replay attacks)
        String nonceKey = appId + ":" + nonce;
        Long previousTimestamp = usedNonces.putIfAbsent(nonceKey, timestamp);
        if (previousTimestamp != null) {
            log.warn("Authentication failed: nonce already used. appId={}, instanceId={}, nonce={}",
                    appId, instanceId, nonce);
            return false;
        }

        // 3. Verify signature
        String expectedSignature = generateSignature(appId, instanceId, timestamp, nonce);
        if (!constantTimeEquals(expectedSignature, signature)) {
            log.warn("Authentication failed: invalid signature. appId={}, instanceId={}",
                    appId, instanceId);
            return false;
        }

        // Clean up old nonces periodically
        cleanupExpiredNonces();

        log.debug("Agent authenticated successfully: appId={}, instanceId={}", appId, instanceId);
        return true;
    }

    /**
     * Generate HMAC-SHA256 signature for the given parameters.
     */
    public String generateSignature(String appId, String instanceId, long timestamp, String nonce) {
        String data = String.format("%s:%s:%d:%s", appId, instanceId, timestamp, nonce);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate signature: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Clean up expired nonces to prevent memory leak.
     */
    private void cleanupExpiredNonces() {
        long expirationTime = System.currentTimeMillis() - TOKEN_VALIDITY_MS * 2;
        usedNonces.entrySet().removeIf(entry -> entry.getValue() < expirationTime);
    }
}
