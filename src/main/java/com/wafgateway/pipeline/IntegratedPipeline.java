package com.wafgateway.pipeline;

import com.wafgateway.common.HttpRequest;
import com.wafgateway.worklet1.Worklet1;
import com.wafgateway.worklet1.Worklet1Config;
import com.wafgateway.worklet1.Worklet1Result;
import com.wafgateway.worklet2.Worklet2ProcessClient;
import com.wafgateway.worklet2.Worklet2Result;
import com.wafgateway.worklet3.ActionExtractor;
import com.wafgateway.worklet3.session.SecureSessionTracker;
import com.wafgateway.worklet3.session.SessionTracker;

public class IntegratedPipeline {

    public record PipelineDecision(boolean allowed, String stage, String sessionId, String action, String details) {
    }

    private static final String BYPASS_ACTION = "GET_STATIC";

    private final Worklet1 worklet1;
    private final Worklet2ProcessClient worklet2;
    private final SecureSessionTracker w3SessionTracker;

    public IntegratedPipeline() throws Exception {
        this.worklet1 = new Worklet1(Worklet1Config.load());
        this.worklet2 = new Worklet2ProcessClient();
        this.w3SessionTracker = new SecureSessionTracker();
    }

    public IntegratedPipeline(Worklet1 worklet1, Worklet2ProcessClient worklet2, SecureSessionTracker w3SessionTracker) {
        this.worklet1 = worklet1;
        this.worklet2 = worklet2;
        this.w3SessionTracker = w3SessionTracker;
    }

    public IntegratedPipeline(Worklet1 worklet1, Worklet2ProcessClient worklet2, SessionTracker sessionTracker) {
        this.worklet1 = worklet1;
        this.worklet2 = worklet2;
        this.w3SessionTracker = new SecureSessionTracker();
    }

    private String resolveAction(HttpRequest request) {
        String method = request.method() != null ? request.method().toUpperCase() : "GET";
        String path = request.path() != null ? request.path() : "/";
        return ActionExtractor.extractAction(method, path);
    }

    public PipelineDecision process(HttpRequest request) {
        String method = request.method() != null ? request.method().toUpperCase() : "GET";
        String path = request.path() != null ? request.path() : "/";
        String action = resolveAction(request);
        System.out.println("[WAF] " + method + " " + path + " -> action=" + action);

        Worklet1Result w1 = worklet1.inspect(request);
        if (w1.blocked()) {
            return new PipelineDecision(false, "Worklet 1 (HTTP/Signatures)", null, action,
                    "Blocked by Worklet 1: " + w1.reason() + " - " + w1.detail());
        }

        String rawSessionId = w1.sessionId();
        if (rawSessionId == null || rawSessionId.isEmpty()) {
            rawSessionId = "session_default";
        }

        // Cryptographically sign the validated session ID for Worklet 3 SecureSessionTracker
        String signedSessionId;
        if (w3SessionTracker.getTokenVerifier().verifySignature(rawSessionId)) {
            signedSessionId = rawSessionId;
        } else {
            signedSessionId = w3SessionTracker.generateSignedSessionId(rawSessionId);
        }

        if (BYPASS_ACTION.equals(action)) {
            return new PipelineDecision(true, "Bypass (Public Route)", signedSessionId, action,
                    "Static/public route bypassed WAF state checks");
        }

        if (!w1.tokens().isEmpty()) {
            Worklet2Result w2 = worklet2.validate(signedSessionId, method, w1.tokens());
            if (!w2.success()) {
                return new PipelineDecision(false, "Worklet 2 (CYK/Structure)", signedSessionId, action,
                        "Blocked by Worklet 2: " + w2.reason() + " - " + w2.rejectReason());
            }
        }

        // Worklet 3: Verification via w3SessionTracker (HMAC check -> TM checkValid)
        boolean validTransition = w3SessionTracker.checkValid(signedSessionId, action);
        if (!validTransition) {
            return new PipelineDecision(false, "Worklet 3 (Turing Machine State)", signedSessionId, action,
                    "Blocked by Worklet 3: Invalid transition for action '" + action + "' in state " +
                            w3SessionTracker.getCurrentState(signedSessionId));
        }

        // Worklet 3: Commit transition to encrypted SecureTape
        w3SessionTracker.commit(signedSessionId, action);

        return new PipelineDecision(true, "Pipeline Complete", signedSessionId, action,
                "Request successfully authorized and state committed: " + action);
    }

    public SecureSessionTracker getW3SessionTracker() {
        return w3SessionTracker;
    }

    public SecureSessionTracker getSecureSessionTracker() {
        return w3SessionTracker;
    }

    public SessionTracker getSessionTracker() {
        return w3SessionTracker.getSessionTracker();
    }
}
