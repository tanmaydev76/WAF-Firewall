package com.wafgateway.pipeline;

import com.wafgateway.common.HttpRequest;
import com.wafgateway.worklet1.Worklet1;
import com.wafgateway.worklet1.Worklet1Config;
import com.wafgateway.worklet1.Worklet1Result;
import com.wafgateway.worklet2.Worklet2ProcessClient;
import com.wafgateway.worklet2.Worklet2Result;
import com.wafgateway.worklet3.session.SessionTracker;

/**
 * End-to-end WAF Pipeline integrating:
 * 1. Worklet 1 (Java)  : HTTP validation, signature scanning & JSON tokenization
 * 2. Worklet 2 (C++)   : CYK CFG parsing, AST building, delimiter/depth security & action extraction
 * 3. Worklet 3 (Java)  : Turing Machine session state machine validation
 */
public class IntegratedPipeline {

    public record PipelineDecision(
            boolean allowed,
            String stage,
            String sessionId,
            String action,
            String details
    ) {}

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

    public PipelineDecision process(HttpRequest request) {
        // Step 1: Worklet 1 - Signature inspection and token extraction
        Worklet1Result w1 = worklet1.inspect(request);
        if (w1.blocked()) {
            return new PipelineDecision(false, "Worklet 1 (HTTP/Signatures)", null, null,
                    "Blocked by Worklet 1: " + w1.reason() + " - " + w1.detail());
        }

        String sessionId = w1.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "session_default";
        }

        // If request has no body tokens, pass through
        if (w1.tokens().isEmpty()) {
            return new PipelineDecision(true, "Worklet 1", sessionId, null,
                    "Non-body request passed signature inspection");
        }

        // Step 2: Worklet 2 - C++ CYK grammar validation and semantic action extraction
        Worklet2Result w2 = worklet2.validate(sessionId, request.method(), w1.tokens());
        if (!w2.success()) {
            return new PipelineDecision(false, "Worklet 2 (CYK/Structure)", sessionId, null,
                    "Blocked by Worklet 2: " + w2.reason() + " - " + w2.rejectReason());
        }

        String action = w2.action();

        // Step 3: Worklet 3 - Turing Machine session state transition check
        boolean validTransition = sessionTracker.checkValid(sessionId, action);
        if (!validTransition) {
            return new PipelineDecision(false, "Worklet 3 (Turing Machine State)", sessionId, action,
                    "Blocked by Worklet 3: Invalid transition for action '" + action + "' in state " +
                            sessionTracker.getCurrentState(sessionId));
        }

        // Commit valid state to Turing Machine tape
        sessionTracker.commit(sessionId, action);

        return new PipelineDecision(true, "Pipeline Complete", sessionId, action,
                "Request successfully authorized and state committed: " + action);
    }

    public SessionTracker getSessionTracker() {
        return sessionTracker;
    }
}
