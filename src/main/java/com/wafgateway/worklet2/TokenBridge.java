package com.wafgateway.worklet2;

import com.wafgateway.common.Token;
import java.util.List;

/**
 * Bridges Java Worklet 1 tokens to the C++ Worklet 2 token stream format.
 */
public final class TokenBridge {

    private TokenBridge() {}

    /**
     * Serializes a list of Worklet 1 Tokens to JSON for transmission to Worklet 2.
     */
    public static String tokensToJson(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String cleanVal = t.value();
            // Worklet 1 stores raw string literals including quotes, e.g. "\"action\""
            if (cleanVal.startsWith("\"") && cleanVal.endsWith("\"") && cleanVal.length() >= 2) {
                cleanVal = cleanVal.substring(1, cleanVal.length() - 1);
            }
            sb.append("  {\"type\": \"").append(t.type().name()).append("\", ")
              .append("\"value\": \"").append(escape(cleanVal)).append("\", ")
              .append("\"position\": ").append(t.position()).append("}");
            if (i < tokens.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
