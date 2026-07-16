package com.example.waorder.service;

import com.example.waorder.config.WhatsAppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Generates and validates a signed, expiring token that carries the WhatsApp
 * user's wa_id safely inside the order-form URL, so the website knows who
 * submitted the form without trusting an unsigned query param.
 *
 * Token format (before base64url): "<wa_id>:<expiryEpochMillis>:<hmacHex>"
 */
@Service
public class LinkTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long DEFAULT_TTL_MILLIS = 30 * 60 * 1000L; // 30 minutes

    private final WhatsAppProperties properties;

    public LinkTokenService(WhatsAppProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String waId) {
        long expiry = System.currentTimeMillis() + DEFAULT_TTL_MILLIS;
        String payload = waId + ":" + expiry;
        String signature = sign(payload);
        String raw = payload + ":" + signature;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the wa_id if the token is valid and not expired, otherwise throws.
     */
    public String validateAndExtractWaId(String token) {
        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed token");
        }
        String waId = parts[0];
        long expiry = Long.parseLong(parts[1]);
        String signature = parts[2];

        String expectedPayload = waId + ":" + expiry;
        String expectedSignature = sign(expectedPayload);
        if (!expectedSignature.equals(signature)) {
            throw new IllegalArgumentException("Invalid token signature");
        }
        if (System.currentTimeMillis() > expiry) {
            throw new IllegalArgumentException("Token expired");
        }
        return waId;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(
                    properties.getLinkSigningSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign token", e);
        }
    }
}
