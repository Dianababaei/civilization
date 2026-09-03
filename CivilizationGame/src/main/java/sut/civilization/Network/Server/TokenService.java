package sut.civilization.Network.Server;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hand-rolled JWT (bonus P3-51): header.payload.signature, signed with
 * HMAC-SHA256 and a secret this server generates fresh at startup. The payload
 * carries the username (sub), the player's role and an expiry stamp. The
 * server re-verifies signature + expiry on EVERY request after login - the
 * token is readable by anyone (it is only Base64) but can never be forged or
 * altered without the server's secret key.
 */
public class TokenService {
    /** 2 hours - longer than any evaluation match. */
    private static final long TTL_MILLIS = 2L * 60 * 60 * 1000;

    private final byte[] secret = new byte[32];

    public TokenService() {
        new SecureRandom().nextBytes(secret);
    }

    public String issue(String username, String role) {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"" + username + "\",\"role\":\"" + role
                + "\",\"exp\":" + (System.currentTimeMillis() + TTL_MILLIS) + "}");
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    /** Null when the token is valid for this username, otherwise the exact problem. */
    public String check(String token, String username) {
        if (token == null || token.isEmpty()) return "Missing auth token - join again.";
        String[] parts = token.split("\\.");
        if (parts.length != 3) return "Malformed auth token.";
        if (!sign(parts[0] + "." + parts[1]).equals(parts[2]))
            return "Invalid token signature - request rejected.";
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "Malformed auth token.";
        }
        if (!payload.contains("\"sub\":\"" + username + "\""))
            return "This token belongs to another player.";
        if (readExp(payload) < System.currentTimeMillis())
            return "Auth token expired - join again.";
        return null;
    }

    private long readExp(String payload) {
        int at = payload.indexOf("\"exp\":");
        if (at < 0) return 0;
        int start = at + 6, end = start;
        while (end < payload.length() && Character.isDigit(payload.charAt(end))) end++;
        try {
            return Long.parseLong(payload.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String b64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
