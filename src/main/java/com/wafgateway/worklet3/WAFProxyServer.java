package com.wafgateway.worklet3;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.wafgateway.common.HttpRequest;
import com.wafgateway.worklet1.Worklet1;
import com.wafgateway.worklet1.Worklet1Config;
import com.wafgateway.worklet1.Worklet1Result;
import com.wafgateway.worklet2.TokenBridge;
import com.wafgateway.worklet3.session.SecureSessionTracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * WAFProxyServer - Central Gateway Orchestrator.
 *
 * Implements the 11-step request pipeline and Two-Phase Commit specified in
 * the W3 Gateway Implementation Guide.
 */
public class WAFProxyServer {

    private final int port;
    private final String backendUrl;
    private Worklet1 w1;
    private W2SubprocessClient w2Client;
    private SecureSessionTracker w3;
    private HttpServer server;

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    public WAFProxyServer() {
        this(8080, "http://localhost:3000");
    }

    public WAFProxyServer(int port, String backendUrl) {
        this.port = port;
        this.backendUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
    }

    public void start() throws Exception {
        // Initialize W1 ONCE at startup (compiles all DFAs)
        Worklet1Config config = Worklet1Config.load();
        w1 = new Worklet1(config);

        // Initialize W2 subprocess client and W3 SecureSessionTracker
        w2Client = new W2SubprocessClient();
        w3 = new SecureSessionTracker();

        // Start HTTP server on port
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new WAFHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("========================================================================");
        System.out.println("   WAF GATEWAY ORCHESTRATOR RUNNING                                     ");
        System.out.println("========================================================================");
        System.out.println("Gateway Port      : http://localhost:" + port);
        System.out.println("Backend Target    : " + backendUrl);
        System.out.println("Architecture      : Gateway Orchestrator (W1 -> W2 -> W3 Two-Phase Commit)");
        System.out.println("========================================================================\n");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class WAFHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // STEP 1: Parse HTTP request
                HttpRequest request = parseRequest(exchange);

                // STEP 2: Call W1 (in-memory, thread-safe inspection)
                Worklet1Result w1Result = w1.inspect(request);

                // STEP 3: W1 decision (short-circuit on signature block)
                if (w1Result.blocked()) {
                    logBlocked("W1 (Signatures)", w1Result.detail(), request);
                    sendGenericBlocked(exchange, 403, "W1_SIGNATURE_BLOCK", w1Result.detail());
                    return;
                }

                // STEP 4: Serialize tokens to JSON (if body present)
                if (!w1Result.tokens().isEmpty()) {
                    String tokensJson = TokenBridge.tokensToJson(w1Result.tokens());

                    // STEP 5: Call W2 (subprocess: pure structural validator)
                    boolean w2Valid;
                    try {
                        w2Valid = w2Client.validateTokens(tokensJson);
                    } catch (Exception e) {
                        logBlocked("W2 (Subprocess)", "Subprocess error: " + e.getMessage(), request);
                        sendGenericBlocked(exchange, 500, "W2_EXEC_ERROR", "Internal Validator Failure");
                        return;
                    }

                    // STEP 6: W2 decision (short-circuit on grammar / depth / bomb failure)
                    if (!w2Valid) {
                        logBlocked("W2 (CYK/Structure)", "Structural validation failed (Grammar / Depth limit)", request);
                        sendGenericBlocked(exchange, 403, "W2_STRUCTURAL_BLOCK", "Malformed structure or nesting limit exceeded");
                        return;
                    }
                }

                // STEP 7: Extract sessionId + action using ActionExtractor helper
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                ActionExtractor.ExtractedFields fields = ActionExtractor.extract(
                        authHeader, request.cookies(), method, path);

                String sessionId = fields.sessionId();
                if (sessionId == null || sessionId.isEmpty()) {
                    sessionId = w1Result.sessionId();
                }
                if ("POST_LOGIN".equals(fields.action())) {
                    if (sessionId == null || !sessionId.contains(".")) {
                        sessionId = w3.generateSignedSessionId(sessionId != null && !sessionId.isEmpty() ? sessionId : "SESSION_001");
                    }
                } else if (sessionId == null || sessionId.isEmpty()) {
                    sessionId = "anon_session";
                }

                // STEP 8: Call W3 (in-memory Turing Machine state check)
                boolean w3Valid = w3.checkValid(sessionId, fields.action());

                // STEP 9: W3 decision (short-circuit on invalid state transition)
                if (!w3Valid) {
                    logBlocked("W3 (Turing Machine State)", "Invalid workflow transition for action: " + fields.action(), request);
                    sendGenericBlocked(exchange, 403, "W3_STATE_VIOLATION", "Invalid session state for action " + fields.action());
                    return;
                }

                // STEP 10: Forward to backend
                HttpResponse<byte[]> backendResponse;
                try {
                    backendResponse = forwardToBackend(exchange, request);
                } catch (Exception e) {
                    logBlocked("BACKEND", "Forwarding error: " + e.getMessage(), request);
                    sendGenericBlocked(exchange, 502, "BACKEND_ERROR", "Backend unreachable");
                    return;
                }

                // STEP 11: Commit TM tape (ONLY if backend succeeded with 200 OK - Two-Phase Commit)
                if (backendResponse.statusCode() == 200) {
                    w3.commit(sessionId, fields.action());
                }

                // Relay backend response back to client
                relayBackendResponse(exchange, backendResponse);

            } catch (Exception e) {
                sendGenericBlocked(exchange, 500, "PROXY_ERROR", e.getMessage());
            }
        }
    }

    private HttpRequest parseRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath() != null ? uri.getPath() : "/";

        Map<String, List<String>> queryMap = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    queryMap.computeIfAbsent(pair.substring(0, eq), k -> new ArrayList<>()).add(pair.substring(eq + 1));
                }
            }
        }

        Map<String, String> headerMap = new HashMap<>();
        Map<String, String> cookieMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            String name = entry.getKey().toLowerCase();
            String val = String.join(", ", entry.getValue());
            headerMap.put(name, val);

            if (name.equals("cookie")) {
                for (String c : val.split(";")) {
                    int eq = c.indexOf('=');
                    if (eq > 0) {
                        cookieMap.put(c.substring(0, eq).trim(), c.substring(eq + 1).trim());
                    }
                }
            }
        }

        byte[] body;
        try (InputStream is = exchange.getRequestBody()) {
            body = is.readAllBytes();
        }

        return new HttpRequest.Builder()
                .method(method)
                .path(path)
                .query(queryMap)
                .headers(headerMap)
                .cookies(cookieMap)
                .body(body)
                .build();
    }

    private HttpResponse<byte[]> forwardToBackend(HttpExchange exchange, HttpRequest request) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        String url = backendUrl + request.path() + (rawQuery != null ? "?" + rawQuery : "");

        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(request.method(), request.body().length > 0 ?
                        java.net.http.HttpRequest.BodyPublishers.ofByteArray(request.body()) :
                        java.net.http.HttpRequest.BodyPublishers.noBody());

        for (Map.Entry<String, String> h : request.headers().entrySet()) {
            if (!HOP_BY_HOP.contains(h.getKey())) {
                try {
                    builder.header(h.getKey(), h.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void relayBackendResponse(HttpExchange exchange, HttpResponse<byte[]> resp) throws IOException {
        for (Map.Entry<String, List<String>> h : resp.headers().map().entrySet()) {
            String name = h.getKey().toLowerCase();
            if (!HOP_BY_HOP.contains(name)) {
                for (String v : h.getValue()) {
                    exchange.getResponseHeaders().add(name, v);
                }
            }
        }
        exchange.getResponseHeaders().set("X-WAF-Status", "ALLOWED");
        byte[] b = resp.body();
        exchange.sendResponseHeaders(resp.statusCode(), b != null ? b.length : 0);
        if (b != null && b.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(b);
            }
        }
    }

    private void sendGenericBlocked(HttpExchange exchange, int status, String code, String reason) throws IOException {
        String json = "{\n" +
                "  \"status\": \"BLOCKED\",\n" +
                "  \"code\": " + status + ",\n" +
                "  \"error\": \"" + code + "\",\n" +
                "  \"message\": \"Request rejected by Web Application Firewall.\"\n" +
                "}\n";
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-WAF-Status", "BLOCKED");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    private void logBlocked(String stage, String detail, HttpRequest req) {
        System.out.println("[WAF ALERT] [" + stage + "] BLOCKED " + req.method() + " " + req.path() + " - " + detail);
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String backend = "http://localhost:3000";
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) port = Integer.parseInt(args[++i]);
            else if ("--target".equals(args[i]) && i + 1 < args.length) backend = args[++i];
        }
        new WAFProxyServer(port, backend).start();
    }
}
