package com.wafgateway.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lightweight mock local website (running on port 3000) for testing the WAF reverse proxy.
 */
public class MockWebsiteServer {

    private final int port;
    private HttpServer server;

    public MockWebsiteServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new SiteHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[Local Website Server] Running at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private static class SiteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            String response;
            int status = 200;

            if ("/api/login".equals(path) && "POST".equalsIgnoreCase(method)) {
                // Set session cookie
                exchange.getResponseHeaders().set("Set-Cookie", "WAF_SID=7f3a2b9c0d1e4f5a6b7c8d9e0f1a2b3c; Path=/; HttpOnly");
                response = "{\"status\":\"OK\",\"message\":\"Login successful! WAF_SID session cookie issued.\"}";
            } else if ("/api/cart".equals(path) && "POST".equalsIgnoreCase(method)) {
                response = "{\"status\":\"OK\",\"message\":\"Item added to cart successfully!\"}";
            } else if ("/api/checkout".equals(path) && "POST".equalsIgnoreCase(method)) {
                response = "{\"status\":\"OK\",\"message\":\"Checkout completed successfully!\"}";
            } else {
                response = "<!DOCTYPE html><html><head><title>Local Website</title></head>" +
                        "<body><h1>Local Website Backend (Port 3000)</h1>" +
                        "<p>Protected by WAF Reverse Proxy (Port 8080).</p>" +
                        "<p>Method: " + method + ", Path: " + path + "</p>" +
                        "</body></html>";
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            if (response.startsWith("{")) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
            } else {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        MockWebsiteServer site = new MockWebsiteServer(port);
        site.start();
    }
}
