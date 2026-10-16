package com.wafgateway.worklet3.session;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Validates that a session ID is cryptographically legitimate.
 * Uses HMAC-SHA256 to verify the session ID was signed by our server.
 * Format of signed session ID: "rawId.signature"
 */
public class SessionTokenVerifier {
    private final String secretHmacKey;
    private final String delimiter = ".";

    public SessionTokenVerifier() {
        this.secretHmacKey = "AutomataGuardHMACKey2026";
    }

    public SessionTokenVerifier(String customKey) {
        this.secretHmacKey = customKey;
    }

    /**
     * Verifies that the session ID has a valid HMAC signature.
     * 
     * @param signedSessionId Format: "rawId.signature"
     * @return true if signature is valid, false if forged or malformed
     */
    public boolean verifySignature(String signedSessionId) {
        if (signedSessionId == null || signedSessionId.isEmpty()) {
            return false;
        }

        // Split "rawId.signature"
        int lastDot = signedSessionId.lastIndexOf(delimiter);
        if (lastDot == -1 || lastDot == 0 || lastDot == signedSessionId.length() - 1) {
            return false; // No signature found
        }

        String rawId = signedSessionId.substring(0, lastDot);
        String providedSignature = signedSessionId.substring(lastDot + 1);

        // Recalculate the HMAC
        String expectedSignature = calculateHmac(rawId);

        // Compare (constant-time comparison to prevent timing attacks)
        return constantTimeEquals(expectedSignature, providedSignature);
    }

    /**
     * Extracts the raw session ID from the signed token.
     * Call this ONLY after verifySignature() returns true.
     * 
     * @param signedSessionId Format: "rawId.signature"
     * @return raw session ID, or null if format is invalid
     */
    public String extractRawId(String signedSessionId) {
        if (signedSessionId == null || signedSessionId.isEmpty()) {
            return null;
        }

        int lastDot = signedSessionId.lastIndexOf(delimiter);
        if (lastDot == -1 || lastDot == 0) {
            return null;
        }

        return signedSessionId.substring(0, lastDot);
    }

    /**
     * Generates a signed session ID (for testing or backend use).
     * 
     * @param rawId The raw session ID (e.g., "SESSION_101")
     * @return Signed session ID: "SESSION_101.a3f5b2c1..."
     */
    public String generateSignedId(String rawId) {
        String signature = calculateHmac(rawId);
        return rawId + delimiter + signature;
    }

    /**
     * Calculates HMAC-SHA256 of the raw ID.
     */
    private String calculateHmac(String rawId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretHmacKey.getBytes(), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(rawId.getBytes());
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC calculation failed", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null)
            return false;
        if (a.length() != b.length())
            return false;

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}