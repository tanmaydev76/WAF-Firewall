package com.wafgateway.pipeline;

import com.wafgateway.common.HttpRequest;
import com.wafgateway.worklet1.Worklet1;
import com.wafgateway.worklet1.Worklet1Config;
import com.wafgateway.worklet1.Worklet1Result;
import com.wafgateway.worklet2.Worklet2ProcessClient;
import com.wafgateway.worklet2.Worklet2Result;
import com.wafgateway.worklet3.session.SessionTracker;

import java.util.HashMap;
import java.util.Map;

public class IntegratedPipeline {

    public record PipelineDecision(boolean allowed, String stage, String sessionId, String action, String details) {}

    private static final Map<String, String> ROUTE_ACTION_MAP = new HashMap<>();

    static {
        ROUTE_ACTION_MAP.put("POST:/api/login",    "POST_LOGIN");
        ROUTE_ACTION_MAP.put("POST:/api/logout",   "POST_LOGOUT");
        ROUTE_ACTION_MAP.put("POST:/api/cart",     "POST_CART");
        ROUTE_ACTION_MAP.put("GET:/api/cart",      "GET_CART");
        ROUTE_ACTION_MAP.put("PUT:/api/cart",      "PUT_CART");
        ROUTE_ACTION_MAP.put("DELETE:/api/cart",   "DELETE_CART");
        ROUTE_ACTION_MAP.put("POST:/api/pay",      "POST_PAY");
        ROUTE_ACTION_MAP.put("POST:/api/checkout", "POST_CHECKOUT");
        ROUTE_ACTION_MAP.put("GET:/",              "GET_HOME");
        ROUTE_ACTION_MAP.put("GET:/api/home",      "GET_HOME");
        ROUTE_ACTION_MAP.put("GET:/favicon.ico",   "GET_STATIC");
        ROUTE_ACTION_MAP.put("GET:/health",        "GET_STATIC");
    }

    private static final String BYPASS_ACTION = "GET_STATIC";

    private final Worklet1 worklet1;
    private final Worklet2ProcessClient worklet2;
    private final SessionTracker sessionTracker;

    public IntegratedPipeline() throws Exception {
        this.worklet1 = new Worklet1(Worklet1Config.load());
        this.worklet2 = new Worklet2ProcessClient();
        this.sessionTracker = new SessionTracker();
    }

    public IntegratedPipeline(Worklet1 worklet1, Worklet2ProcessClient worklet2, SessionTracker sessionTracker) {
        this.worklet1 = worklet1;
        this.worklet2 = worklet2;
        this.sessionTracker = sessionTracker;
    }

    private String resolveAction(HttpRequest request) {
        String method = request.method() != null ? request.method().toUpperCase() : "GET";
        String path   = request.path()   != null ? request.path() : "/";
        String key = method + ":" + path;
        if (ROUTE_ACTION_MAP.containsKey(key)) {
            return ROUTE_ACTION_MAP.get(key);
        }
        String bestMatch = null;
        int bestLen = -1;
        for (Map.Entry<String, String> entry : ROUTE_ACTION_MAP.entrySet()) {
            String entryKey = entry.getKey();
            int colon = entryKey.indexOf(':');
            if (colon < 0) continue;
            String entryMethod = entryKey.substring(0, colon);
            String entryPath   = entryKey.substring(colon + 1);
            if (entryMethod.equals(method) && path.startsWith(entryPath) && entryPath.length() > bestLen) {
                bestLen = entryPath.length();
                bestMatch = entry.getValue();
            }
        }
        if (bestMatch != null) return bestMatch;
        return method + "_UNKNOWN";
    }

    public PipelineDecision process(HttpRequest request) {
        String method = request.method() != null ? request.method().toUpperCase() : "GET";
        String path   = request.path()   != null ? request.path() : "/";
        String action = resolveAction(request);
        System.out.println("[WAF] " + method + " " + path + " -> action=" + action);

        Worklet1Result w1 = worklet1.inspect(request);
        if (w1.blocked()) {
            return new PipelineDecision(false, "Worklet 1 (HTTP/Signatures)", null, action,
                    "Blocked by Worklet 1: " + w1.reason() + " - " + w1.detail());
        }

        String sessionId = w1.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "session_default";
        }

        if (BYPASS_ACTION.equals(action)) {
            return new PipelineDecision(true, "Bypass (Public Route)", sessionId, action,
                    "Static/public route bypassed WAF state checks");
        }

        if (!w1.tokens().isEmpty()) {
            Worklet2Result w2 = worklet2.validate(sessionId, method, w1.tokens());
            if (!w2.success()) {
                return new PipelineDecision(false, "Worklet 2 (CYK/Structure)", sessionId, action,
                        "Blocked by Worklet 2: " + w2.reason() + " - " + w2.rejectReason());
            }
        }

        boolean validTransition = sessionTracker.checkValid(sessionId, action);
        if (!validTransition) {
            return new PipelineDecision(false, "Worklet 3 (Turing Machine State)", sessionId, action,
                    "Blocked by Worklet 3: Invalid transition for action '" + action + "' in state " +
                            sessionTracker.getCurrentState(sessionId));
        }

        sessionTracker.commit(sessionId, action);

        return new PipelineDecision(true, "Pipeline Complete", sessionId, action,
                "Request successfully authorized and state committed: " + action);
    }

    public SessionTracker getSessionTracker() {
        return sessionTracker;
    }
}
