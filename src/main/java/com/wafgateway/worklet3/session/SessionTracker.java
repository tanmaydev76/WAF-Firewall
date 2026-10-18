package com.wafgateway.worklet3.session;

import com.wafgateway.worklet3.engine.TuringMachine;
import com.wafgateway.worklet3.engine.SecureRuleBook;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Base session manager — handles ConcurrentHashMap of sessions.
 * Does NOT handle HMAC validation (that's in SecureSessionTracker).
 */
public class SessionTracker {
    private ConcurrentHashMap<String, TuringMachine> sessions;
    private SecureRuleBook sharedRuleBook;
    private final int MAX_SESSIONS;

    public SessionTracker() {
        this.sessions = new ConcurrentHashMap<>();
        this.sharedRuleBook = new SecureRuleBook();
        this.MAX_SESSIONS = 10000;
    }

    public SessionTracker(int maxSessions) {
        this.sessions = new ConcurrentHashMap<>();
        this.sharedRuleBook = new SecureRuleBook();
        this.MAX_SESSIONS = maxSessions;
    }

    /**
     * Phase 1: Check if the action is valid for this session.
     * Does NOT advance the tape.
     *
     * @param rawSessionId The RAW session ID (HMAC already verified by
     *                     SecureSessionTracker)
     * @param action       The requested action (e.g., "POST_LOGIN", "POST_CART")
     * @return true if valid, false if blocked
     */
    public boolean checkValid(String rawSessionId, String action) {
        if (rawSessionId == null || action == null) {
            return false;
        }

        TuringMachine tm = sessions.get(rawSessionId);

        // CASE 1: New session (not in map)
        if (tm == null) {
            if (action.equals("POST_LOGIN")) {
                // First-action-login rule: only POST_LOGIN allowed for new sessions
                if (sessions.size() >= MAX_SESSIONS) {
                    return false; // Memory exhaustion protection
                }
                tm = createNewSession(rawSessionId);
                return tm.checkValid("POST_LOGIN");
            } else {
                // Block! Must login first
                return false;
            }
        }

        // CASE 2: Existing session
        if (action.equals("POST_LOGOUT")) {
            return true; // Logout is always allowed for an active session
        }

        boolean isValid = tm.checkValid(action);

        if (!isValid) {
            // Strict security: destroy session on invalid transition
            clearSession(rawSessionId);
        }

        return isValid;
    }

    /**
     * Phase 2: Commit the action to the tape.
     * Only call this AFTER backend returns 200 OK.
     *
     * @param rawSessionId The RAW session ID
     * @param action       The action to commit
     */
    public void commit(String rawSessionId, String action) {
        if (rawSessionId == null || action == null) {
            return;
        }

        // SPECIAL: POST_LOGOUT destroys the session entirely
        if (action.equals("POST_LOGOUT")) {
            clearSession(rawSessionId);
            return;
        }

        TuringMachine tm = sessions.get(rawSessionId);
        if (tm == null && action.equals("POST_LOGIN")) {
            if (sessions.size() < MAX_SESSIONS) {
                tm = createNewSession(rawSessionId);
            }
        }
        if (tm != null) {
            tm.commit(action);
        }
    }

    /**
     * Removes a session from the map.
     * Java GC will destroy the TM and its encrypted tape.
     */
    public void clearSession(String rawSessionId) {
        sessions.remove(rawSessionId);
    }

    /**
     * Creates a new Turing Machine for a new session.
     */
    private TuringMachine createNewSession(String rawSessionId) {
        TuringMachine tm = new TuringMachine(sharedRuleBook);
        sessions.put(rawSessionId, tm);
        return tm;
    }

    /**
     * Returns the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Clears all sessions (for testing or shutdown).
     */
    public void clearAllSessions() {
        sessions.clear();
    }

    /**
     * Returns the current state of a session (for debugging/logging).
     */
    public String getCurrentState(String rawSessionId) {
        TuringMachine tm = sessions.get(rawSessionId);
        if (tm == null) {
            return "NO_SESSION";
        }
        return tm.getCurrentState();
    }
}