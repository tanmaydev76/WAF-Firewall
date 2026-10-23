package com.wafgateway.worklet3;

import java.util.Map;

/**
 * Extracts sessionId and action from HTTP request metadata.
 * Replaces W2's old C++ parse-tree ActionExtractor with pure, high-speed Java evaluation.
 *
 * Thread-safety: stateless, thread-safe.
 */
public final class ActionExtractor {

    private ActionExtractor() {}

    /**
     * Extract sessionId from Authorization header ("Bearer <sid>") or cookie map.
     */
    public static String extractSessionId(String authHeader, Map<String, String> cookies) {
        if (authHeader != null && !authHeader.isEmpty()) {
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            return authHeader.trim();
        }
        if (cookies != null) {
            String sid = cookies.get("WAF_SID");
            if (sid != null && !sid.isEmpty()) return sid.trim();
            sid = cookies.get("waf_sid");
            if (sid != null && !sid.isEmpty()) return sid.trim();
            sid = cookies.get("SESSION_ID");
            if (sid != null && !sid.isEmpty()) return sid.trim();
        }
        return null;
    }

    /**
     * Derive action string from HTTP method + URL path.
     * e.g., POST + /api/login -> "POST_LOGIN"
     */
    public static String extractAction(String method, String path) {
        if (method == null || path == null) return "UNKNOWN";
        String m = method.toUpperCase().trim();
        String p = path.toLowerCase().trim();

        // Exact endpoint action mappings from W3 State Machine Guide
        if (p.equals("/api/login")    && m.equals("POST"))   return "POST_LOGIN";
        if (p.equals("/api/logout")   && m.equals("POST"))   return "POST_LOGOUT";
        if (p.equals("/api/cart")     && m.equals("POST"))   return "POST_CART";
        if (p.equals("/api/cart")     && m.equals("GET"))    return "GET_CART";
        if (p.equals("/api/cart")     && m.equals("PUT"))    return "PUT_CART";
        if (p.equals("/api/cart")     && m.equals("DELETE")) return "DELETE_CART";
        if (p.equals("/api/pay")      && m.equals("POST"))   return "POST_PAY";
        if (p.equals("/api/pay")      && m.equals("DELETE")) return "CANCEL_PAY";
        if (p.equals("/api/checkout") && m.equals("POST"))   return "POST_CHECKOUT";
        if (p.equals("/api/home")     && m.equals("GET"))    return "GET_HOME";
        if (p.equals("/api/receipt")  && m.equals("GET"))    return "GET_RECEIPT";

        // Fallbacks for subpaths: e.g. /api/cart/add -> POST_CART
        if (p.startsWith("/api/cart") && m.equals("POST")) return "POST_CART";
        if (p.startsWith("/api/checkout") && m.equals("POST")) return "POST_CHECKOUT";
        if (p.startsWith("/api/login") && m.equals("POST")) return "POST_LOGIN";

        return m + "_" + p.replaceAll("^/api/", "").replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    /**
     * Container record for extracted fields.
     */
    public record ExtractedFields(String sessionId, String action) {}

    public static ExtractedFields extract(String authHeader, Map<String, String> cookies, String method, String path) {
        return new ExtractedFields(
                extractSessionId(authHeader, cookies),
                extractAction(method, path)
        );
    }
}
