package com.wafgateway.worklet3.session;

/**
 * Security wrapper — adds HMAC validation to SessionTracker.
 * This is the ENTRY POINT that WAFProxyServer calls.
 *
 * Pattern: Similar to SecureTape wrapping Tape.
 */
public class SecureSessionTracker {
    private SessionTracker sessionTracker;
    private SessionTokenVerifier tokenVerifier;

    public SecureSessionTracker() {
        this.sessionTracker = new SessionTracker();
        this.tokenVerifier = new SessionTokenVerifier();
    }

    public SecureSessionTracker(int maxSessions) {
        this.sessionTracker = new SessionTracker(maxSessions);
        this.tokenVerifier = new SessionTokenVerifier();
    }

    /**
     * Phase 1: Verify HMAC signature, then check if action is valid.
     *
     * @param signedSessionId Format: "rawId.signature"
     * @param action          The requested action
     * @return true if valid, false if blocked or signature invalid
     */
    public boolean checkValid(String signedSessionId, String action) {
        // Step 1: Verify HMAC signature
        if (!tokenVerifier.verifySignature(signedSessionId)) {
            return false; // Forged session ID — block immediately
        }

        // Step 2: Extract raw ID
        String rawId = tokenVerifier.extractRawId(signedSessionId);
        if (rawId == null) {
            return false;
        }

        // Step 3: Delegate to SessionTracker
        return sessionTracker.checkValid(rawId, action);
    }

    /**
     * Phase 2: Commit the action (after backend 200 OK).
     *
     * @param signedSessionId Format: "rawId.signature"
     * @param action          The action to commit
     */
    public void commit(String signedSessionId, String action) {
        // Step 1: Verify HMAC signature
        if (!tokenVerifier.verifySignature(signedSessionId)) {
            return; // Forged — ignore
        }

        // Step 2: Extract raw ID
        String rawId = tokenVerifier.extractRawId(signedSessionId);
        if (rawId == null) {
            return;
        }

        // Step 3: Delegate to SessionTracker
        sessionTracker.commit(rawId, action);
    }

    /**
     * Returns the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessionTracker.getActiveSessionCount();
    }

    /**
     * Returns the current state of a session (for debugging).
     */
    public String getCurrentState(String signedSessionId) {
        if (!tokenVerifier.verifySignature(signedSessionId)) {
            return "INVALID_SIGNATURE";
        }
        String rawId = tokenVerifier.extractRawId(signedSessionId);
        return sessionTracker.getCurrentState(rawId);
    }

    /**
     * Clears all sessions (for testing).
     */
    public void clearAllSessions() {
        sessionTracker.clearAllSessions();
    }

    // ==========================================
    // Helper methods for testing
    // ==========================================

    /**
     * Generates a signed session ID for testing purposes.
     */
    public String generateSignedSessionId(String rawId) {
        return tokenVerifier.generateSignedId(rawId);
    }

    public SessionTracker getSessionTracker() {
        return sessionTracker;
    }

    public SessionTokenVerifier getTokenVerifier() {
        return tokenVerifier;
    }
}