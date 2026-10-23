package com.wafgateway.worklet2;

import com.wafgateway.common.Token;
import com.wafgateway.common.TokenType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client that invokes the C++ Worklet 2 binary (CYK Grammar Parser & Structural Validator)
 * through standard IPC/pipes, with an integrated fallback evaluator.
 */
public class Worklet2ProcessClient {

    private final String binaryPath;

    public Worklet2ProcessClient() {
        this(findBinaryPath());
    }

    public Worklet2ProcessClient(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    private static String findBinaryPath() {
        String[] candidates = {
            "worklet2/worklet2_demo.exe",
            "worklet2/worklet2_demo",
            "../worklet2/worklet2_demo.exe"
        };
        for (String c : candidates) {
            if (Files.exists(Path.of(c))) {
                return Path.of(c).toAbsolutePath().toString();
            }
        }
        return "worklet2/worklet2_demo.exe";
    }

    public Worklet2Result validate(String sessionId, String httpMethod, List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Worklet2Result.rejected(sessionId, "EMPTY_INPUT", "Reject Reason: Token stream is empty.");
        }
        if (tokens.size() > 500) {
            return Worklet2Result.rejected(sessionId, "TOKEN_LIMIT_EXCEEDED", "Reject Reason: Token limit exceeded (Potential JSON bomb attack).");
        }

        // Attempt invocation of native Worklet 2 C++ engine
        if (Files.exists(Path.of(binaryPath))) {
            try {
                return executeNative(sessionId, httpMethod, tokens);
            } catch (Exception ignored) {
                // Fall back to evaluator below
            }
        }

        return evaluateJavaFallback(sessionId, httpMethod, tokens);
    }

    private Worklet2Result executeNative(String sessionId, String httpMethod, List<Token> tokens) throws IOException, InterruptedException {
        String tokenJson = TokenBridge.tokensToJson(tokens);
        ProcessBuilder pb = new ProcessBuilder(
                binaryPath,
                "--pipe",
                sessionId != null ? sessionId : "anon",
                httpMethod != null ? httpMethod : "POST"
        );
        Process proc = pb.start();

        try (OutputStream os = proc.getOutputStream()) {
            os.write(tokenJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        proc.waitFor();

        boolean success = output.contains("\"success\": true");
        String action = extractField(output, "action");
        // Normalize to uppercase so it matches TM rule keys (POST_LOGIN, POST_CART, etc.)
        if (action != null && !action.isEmpty()) {
            action = action.toUpperCase();
        }
        String reason = extractField(output, "reason");
        String rejectReason = extractField(output, "rejectReason");

        Map<String, String> pairs = new HashMap<>();
        if (action != null && !action.isEmpty()) {
            pairs.put("action", action);
        }

        if (success) {
            return Worklet2Result.allowed(sessionId, action, pairs);
        } else {
            return Worklet2Result.rejected(sessionId, reason, rejectReason);
        }
    }

    private static String extractField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private Worklet2Result evaluateJavaFallback(String sessionId, String httpMethod, List<Token> tokens) {
        int depth = 0;
        int maxDepth = 0;
        Deque<TokenType> stack = new ArrayDeque<>();
        String extractedAction = "";

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.LBRACE) {
                stack.push(TokenType.LBRACE);
                depth++;
                if (depth > maxDepth) maxDepth = depth;
            } else if (t.type() == TokenType.RBRACE) {
                if (stack.isEmpty() || stack.pop() != TokenType.LBRACE) {
                    return Worklet2Result.rejected(sessionId, "TAG_MISMATCH", "Structural Tag Mismatch: Unbalanced braces detected.");
                }
                depth--;
            }

            if (t.type() == TokenType.STRING && ("action".equalsIgnoreCase(t.value()) || "\"action\"".equalsIgnoreCase(t.value()))) {
                if (i + 2 < tokens.size() && tokens.get(i + 1).type() == TokenType.COLON) {
                    Token valTok = tokens.get(i + 2);
                    String val = valTok.value();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    extractedAction = val;
                }
            }
        }

        if (!stack.isEmpty()) {
            return Worklet2Result.rejected(sessionId, "TAG_MISMATCH", "Structural Tag Mismatch: Unbalanced braces detected.");
        }
        if (maxDepth > 3) {
            return Worklet2Result.rejected(sessionId, "DEPTH_EXCEEDED", "Security Error: JSON nesting depth exceeded MAX_DEPTH limit.");
        }

        // Normalize to uppercase to match TM rule keys (e.g. POST_LOGIN, GET_CART)
        String finalAction = (httpMethod != null && !httpMethod.isEmpty() && !extractedAction.isEmpty())
                ? (httpMethod.toUpperCase() + "_" + extractedAction.toUpperCase())
                : extractedAction.toUpperCase();

        return Worklet2Result.allowed(sessionId, finalAction, Map.of("action", extractedAction.toUpperCase()));
    }
}
