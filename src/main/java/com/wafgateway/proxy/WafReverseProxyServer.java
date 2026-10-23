package com.wafgateway.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.wafgateway.common.HttpRequest;
import com.wafgateway.pipeline.IntegratedPipeline;
import com.wafgateway.pipeline.IntegratedPipeline.PipelineDecision;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Reverse Proxy WAF Server.
 *
 * <p>Listens on a local port (e.g. 8080), intercepts all incoming HTTP traffic,
 * executes the 3-Worklet WAF Pipeline (Worklet 1 Regex/Tokens -> Worklet 2 C++ CYK -> Worklet 3 TM State),
 * and if authorized, transparently forwards the request to your local website on its target port (e.g. 3000).
 */
public class WafReverseProxyServer {

    private final int proxyPort;
    private final String targetBaseUrl;
    private final IntegratedPipeline pipeline;
    private final HttpClient httpClient;
    private HttpServer server;

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    public WafReverseProxyServer(int proxyPort, String targetBaseUrl) throws Exception {
        this.proxyPort = proxyPort;
        this.targetBaseUrl = targetBaseUrl.endsWith("/") ? targetBaseUrl.substring(0, targetBaseUrl.length() - 1) : targetBaseUrl;
        this.pipeline = new IntegratedPipeline();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(proxyPort), 0);
        server.createContext("/", new ProxyHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("========================================================================");
        System.out.println("   WAF FIREWALL REVERSE PROXY ACTIVE                                    ");
        System.out.println("========================================================================");
        System.out.println("Proxy Port        : http://localhost:" + proxyPort + " (Point your browser/frontend here)");
        System.out.println("Target Website    : " + targetBaseUrl + " (Your actual local website)");
        System.out.println("Inspection Stages : Worklet 1 (Regex/Signatures) -> Worklet 2 (C++ CYK) -> Worklet 3 (TM)");
        System.out.println("========================================================================\n");
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // 1. Convert incoming HttpExchange to com.wafgateway.common.HttpRequest
                HttpRequest request = buildHttpRequest(exchange);

                System.out.println("--> [" + request.method() + "] " + request.path() +
                        (exchange.getRequestURI().getRawQuery() != null ? "?" + exchange.getRequestURI().getRawQuery() : "") +
                        " (from " + exchange.getRemoteAddress() + ")");

                // 2. Run the 3-Worklet WAF Pipeline
                PipelineDecision decision = pipeline.process(request);

                if (!decision.allowed()) {
                    // BLOCKED: Return HTTP 403 Forbidden immediately
                    System.out.println("    [BLOCKED] by " + decision.stage() + ": " + decision.details());
                    sendBlockedResponse(exchange, decision);
                    return;
                }

                // ALLOWED: Forward request to actual local website
                System.out.println("    [ALLOWED] stage=" + decision.stage() +
                        (decision.action() != null ? " action=" + decision.action() : "") +
                        " -> forwarding to " + targetBaseUrl);
                try {
                    forwardToTarget(exchange, request);
                } catch (Exception fwdEx) {
                    // Backend (target website) is unreachable — rollback TM state
                    if (decision.sessionId() != null && decision.action() != null) {
                        pipeline.getSessionTracker().clearSession(decision.sessionId());
                    }
                    System.err.println("    [FORWARD_ERROR] Target " + targetBaseUrl +
                            " is unreachable: " + fwdEx.getMessage());
                    System.err.println("    >> Make sure your website/mock server is running on port 3000!");
                    byte[] errBytes = ("{\"error\":\"Target Unreachable\"," +
                            "\"details\":\"The WAF approved your request but the backend server at '" +
                            targetBaseUrl + "' is not running. Start it first.\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("X-WAF-Verdict", "PASSED");
                    exchange.getResponseHeaders().set("X-WAF-Forward-Error", "TARGET_UNREACHABLE");
                    exchange.sendResponseHeaders(502, errBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(errBytes); }
                }

            } catch (Exception e) {
                System.err.println("Proxy error: " + e.getMessage());
                byte[] errBytes = ("{\"error\":\"Internal Proxy Error\",\"details\":\"" + e.getMessage() + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errBytes);
                }
            }
        }
    }

    private HttpRequest buildHttpRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath() != null ? uri.getPath() : "/";

        // Query parameters
        Map<String, List<String>> queryMap = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq);
                    String v = pair.substring(eq + 1);
                    queryMap.computeIfAbsent(k, key -> new ArrayList<>()).add(v);
                } else if (!pair.isEmpty()) {
                    queryMap.computeIfAbsent(pair, key -> new ArrayList<>()).add("");
                }
            }
        }

        // Headers & Cookies
        Map<String, String> headerMap = new HashMap<>();
        Map<String, String> cookieMap = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            String headerName = entry.getKey().toLowerCase();
            String headerValue = String.join(", ", entry.getValue());
            headerMap.put(headerName, headerValue);

            if (headerName.equalsIgnoreCase("cookie")) {
                for (String cookieStr : headerValue.split(";")) {
                    int eq = cookieStr.indexOf('=');
                    if (eq > 0) {
                        String cKey = cookieStr.substring(0, eq).trim();
                        String cVal = cookieStr.substring(eq + 1).trim();
                        cookieMap.put(cKey, cVal);
                    }
                }
            }
        }

        // Body
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

    private void sendBlockedResponse(HttpExchange exchange, PipelineDecision decision) throws IOException {
        String json = "{\n" +
                "  \"status\": \"BLOCKED\",\n" +
                "  \"code\": 403,\n" +
                "  \"stage\": \"" + escape(decision.stage()) + "\",\n" +
                "  \"action\": \"" + escape(decision.action() != null ? decision.action() : "NONE") + "\",\n" +
                "  \"details\": \"" + escape(decision.details()) + "\",\n" +
                "  \"waf\": \"WAF-Firewall v1.0 (Worklet 1/2/3)\"\n" +
                "}\n";

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("X-WAF-Verdict", "BLOCKED");
        exchange.getResponseHeaders().set("X-WAF-Stage", decision.stage());
        exchange.sendResponseHeaders(403, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void forwardToTarget(HttpExchange exchange, HttpRequest request) throws Exception {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        String targetUrl = targetBaseUrl + request.path() + (rawQuery != null ? "?" + rawQuery : "");

        java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .method(request.method(), request.body().length > 0 ?
                        java.net.http.HttpRequest.BodyPublishers.ofByteArray(request.body()) :
                        java.net.http.HttpRequest.BodyPublishers.noBody());

        // Relay original headers (excluding hop-by-hop headers)
        for (Map.Entry<String, String> h : request.headers().entrySet()) {
            String name = h.getKey().toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(name)) {
                try {
                    reqBuilder.header(name, h.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Add WAF identification header
        reqBuilder.header("X-Forwarded-For", exchange.getRemoteAddress().getAddress().getHostAddress());
        reqBuilder.header("X-WAF-Protected", "true");

        java.net.http.HttpRequest targetReq = reqBuilder.build();
        HttpResponse<byte[]> targetResp = httpClient.send(targetReq, HttpResponse.BodyHandlers.ofByteArray());

        // Relay target headers back to client
        for (Map.Entry<String, List<String>> h : targetResp.headers().map().entrySet()) {
            String name = h.getKey().toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(name)) {
                for (String v : h.getValue()) {
                    exchange.getResponseHeaders().add(name, v);
                }
            }
        }

        exchange.getResponseHeaders().set("X-WAF-Verdict", "PASSED");
        byte[] respBody = targetResp.body();
        exchange.sendResponseHeaders(targetResp.statusCode(), respBody != null ? respBody.length : 0);
        if (respBody != null && respBody.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBody);
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    public static void main(String[] args) {
        int proxyPort = 8080;
        String targetUrl = "http://localhost:3000";

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                proxyPort = Integer.parseInt(args[++i]);
            } else if ("--target".equals(args[i]) && i + 1 < args.length) {
                targetUrl = args[++i];
            }
        }

        try {
            WafReverseProxyServer proxy = new WafReverseProxyServer(proxyPort, targetUrl);
            proxy.start();

            System.out.println("Press Ctrl+C to terminate the proxy server.");
        } catch (Exception e) {
            System.err.println("Failed to start WAF Proxy: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
