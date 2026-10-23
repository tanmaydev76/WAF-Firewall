package com.wafgateway.worklet2;

import java.util.Map;

/**
 * Result returned by Worklet 2 (CYK Grammar Parser & Structural Validator).
 */
public record Worklet2Result(
        boolean success,
        String sessionId,
        String action,
        String reason,
        String rejectReason,
        Map<String, String> extractedPairs
) {
    public static Worklet2Result allowed(String sessionId, String action, Map<String, String> pairs) {
        return new Worklet2Result(true, sessionId, action, "NONE", "", pairs != null ? pairs : Map.of());
    }

    public static Worklet2Result rejected(String sessionId, String reason, String rejectReason) {
        return new Worklet2Result(false, sessionId, "", reason, rejectReason, Map.of());
    }
}
