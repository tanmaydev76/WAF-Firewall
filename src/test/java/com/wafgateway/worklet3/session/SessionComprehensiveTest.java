package com.wafgateway.worklet3.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Session Package - Comprehensive Test Suite (25 Tests)")
public class SessionComprehensiveTest {

    private SecureSessionTracker secureTracker;
    private SessionTracker baseTracker;
    private SessionTokenVerifier tokenVerifier;

    @BeforeEach
    void setUp() {
        secureTracker = new SecureSessionTracker();
        baseTracker = new SessionTracker();
        tokenVerifier = new SessionTokenVerifier();
    }

    // ==========================================
    // 1. SessionTokenVerifier TESTS (6)
    // ==========================================

    @Test
    @DisplayName("ST_01: Valid signed session ID should pass verification")
    void testValidSignedSessionId() {
        String signedId = tokenVerifier.generateSignedId("SESSION_101");
        assertTrue(tokenVerifier.verifySignature(signedId), "Valid signature should pass");
    }

    @Test
    @DisplayName("ST_02: Forged session ID should fail verification")
    void testForgedSessionId() {
        String signedId = tokenVerifier.generateSignedId("SESSION_101");
        // Tamper with the signature
        String forgedId = signedId.substring(0, signedId.length() - 2) + "XX";
        assertFalse(tokenVerifier.verifySignature(forgedId), "Forged signature should fail");
    }

    @Test
    @DisplayName("ST_03: Session ID without signature should fail")
    void testSessionIdWithoutSignature() {
        assertFalse(tokenVerifier.verifySignature("SESSION_101"), "ID without signature should fail");
    }

    @Test
    @DisplayName("ST_04: Null session ID should fail verification")
    void testNullSessionId() {
        assertFalse(tokenVerifier.verifySignature(null), "Null ID should fail");
    }

    @Test
    @DisplayName("ST_05: Empty session ID should fail verification")
    void testEmptySessionId() {
        assertFalse(tokenVerifier.verifySignature(""), "Empty ID should fail");
    }

    @Test
    @DisplayName("ST_06: extractRawId should return the correct raw ID")
    void testExtractRawId() {
        String signedId = tokenVerifier.generateSignedId("USER_999");
        String rawId = tokenVerifier.extractRawId(signedId);
        assertEquals("USER_999", rawId, "Should extract correct raw ID");
    }

    // ==========================================
    // 2. First-Action-Login Rule TESTS (4)
    // ==========================================

    @Test
    @DisplayName("ST_07: New session with POST_LOGIN should be allowed")
    void testNewSessionLoginAllowed() {
        String signedId = secureTracker.generateSignedSessionId("NEW_USER_1");
        assertTrue(secureTracker.checkValid(signedId, "POST_LOGIN"), "New session POST_LOGIN should be allowed");
    }

    @Test
    @DisplayName("ST_08: New session with POST_CART should be BLOCKED")
    void testNewSessionCartBlocked() {
        String signedId = secureTracker.generateSignedSessionId("NEW_USER_2");
        assertFalse(secureTracker.checkValid(signedId, "POST_CART"), "New session POST_CART should be blocked");
    }

    @Test
    @DisplayName("ST_09: New session with POST_PAY should be BLOCKED")
    void testNewSessionPayBlocked() {
        String signedId = secureTracker.generateSignedSessionId("NEW_USER_3");
        assertFalse(secureTracker.checkValid(signedId, "POST_PAY"), "New session POST_PAY should be blocked");
    }

    @Test
    @DisplayName("ST_10: New session with POST_CHECKOUT should be BLOCKED")
    void testNewSessionCheckoutBlocked() {
        String signedId = secureTracker.generateSignedSessionId("NEW_USER_4");
        assertFalse(secureTracker.checkValid(signedId, "POST_CHECKOUT"), "New session POST_CHECKOUT should be blocked");
    }

    // ==========================================
    // 3. Full Workflow TESTS (4)
    // ==========================================

    @Test
    @DisplayName("ST_11: Full happy path workflow through SecureSessionTracker")
    void testFullHappyPath() {
        String signedId = secureTracker.generateSignedSessionId("USER_101");

        assertTrue(secureTracker.checkValid(signedId, "POST_LOGIN"));
        secureTracker.commit(signedId, "POST_LOGIN");

        assertTrue(secureTracker.checkValid(signedId, "POST_CART"));
        secureTracker.commit(signedId, "POST_CART");

        assertTrue(secureTracker.checkValid(signedId, "POST_PAY"));
        secureTracker.commit(signedId, "POST_PAY");

        assertTrue(secureTracker.checkValid(signedId, "POST_CHECKOUT"));
        secureTracker.commit(signedId, "POST_CHECKOUT");

        assertEquals("CONFIRM", secureTracker.getCurrentState(signedId));
    }

    @Test
    @DisplayName("ST_12: State-skip attack should be blocked and session destroyed")
    void testStateSkipAttackBlocked() {
        String signedId = secureTracker.generateSignedSessionId("ATTACKER_1");

        assertTrue(secureTracker.checkValid(signedId, "POST_LOGIN"));
        secureTracker.commit(signedId, "POST_LOGIN");

        // Try to skip to checkout (should be blocked)
        assertFalse(secureTracker.checkValid(signedId, "POST_CHECKOUT"));

        // Session should be destroyed
        assertEquals("NO_SESSION", secureTracker.getCurrentState(signedId));
    }

    @Test
    @DisplayName("ST_13: POST_LOGOUT should destroy session entirely")
    void testLogoutDestroysSession() {
        String signedId = secureTracker.generateSignedSessionId("USER_102");

        secureTracker.checkValid(signedId, "POST_LOGIN");
        secureTracker.commit(signedId, "POST_LOGIN");

        secureTracker.checkValid(signedId, "POST_CART");
        secureTracker.commit(signedId, "POST_CART");

        // Logout
        secureTracker.commit(signedId, "POST_LOGOUT");

        // Session should be destroyed
        assertEquals("NO_SESSION", secureTracker.getCurrentState(signedId));
        assertEquals(0, secureTracker.getActiveSessionCount());
    }

    @Test
    @DisplayName("ST_14: Backward navigation should work through SecureSessionTracker")
    void testBackwardNavigation() {
        String signedId = secureTracker.generateSignedSessionId("USER_103");

        secureTracker.commit(signedId, "POST_LOGIN");
        secureTracker.commit(signedId, "POST_CART");
        secureTracker.commit(signedId, "POST_PAY");

        // Cancel payment — go back to CART
        assertTrue(secureTracker.checkValid(signedId, "CANCEL_PAY"));
        secureTracker.commit(signedId, "CANCEL_PAY");
        assertEquals("CART", secureTracker.getCurrentState(signedId));
    }

    // ==========================================
    // 4. Multi-User Isolation TESTS (3)
    // ==========================================

    @Test
    @DisplayName("ST_15: Two users should have independent sessions")
    void testTwoUsersIndependent() {
        String signedId1 = secureTracker.generateSignedSessionId("USER_A");
        String signedId2 = secureTracker.generateSignedSessionId("USER_B");

        // User A logs in and adds cart
        secureTracker.commit(signedId1, "POST_LOGIN");
        secureTracker.commit(signedId1, "POST_CART");

        // User B logs in only
        secureTracker.commit(signedId2, "POST_LOGIN");

        assertEquals("CART", secureTracker.getCurrentState(signedId1));
        assertEquals("AUTH", secureTracker.getCurrentState(signedId2));
    }

    @Test
    @DisplayName("ST_16: Invalid action by User A should NOT affect User B")
    void testInvalidActionDoesNotAffectOtherUser() {
        String signedId1 = secureTracker.generateSignedSessionId("USER_A");
        String signedId2 = secureTracker.generateSignedSessionId("USER_B");

        secureTracker.commit(signedId1, "POST_LOGIN");
        secureTracker.commit(signedId2, "POST_LOGIN");

        // User A tries invalid action
        assertFalse(secureTracker.checkValid(signedId1, "POST_CHECKOUT"));

        // User A's session is destroyed
        assertEquals("NO_SESSION", secureTracker.getCurrentState(signedId1));

        // User B's session is still active
        assertEquals("AUTH", secureTracker.getCurrentState(signedId2));
    }

    @Test
    @DisplayName("ST_17: Multiple users can complete workflow simultaneously")
    void testMultipleUsersCompleteWorkflow() {
        String[] ids = { "U1", "U2", "U3", "U4", "U5" };
        String[] signedIds = new String[5];

        for (int i = 0; i < 5; i++) {
            signedIds[i] = secureTracker.generateSignedSessionId(ids[i]);
            secureTracker.commit(signedIds[i], "POST_LOGIN");
            secureTracker.commit(signedIds[i], "POST_CART");
            secureTracker.commit(signedIds[i], "POST_PAY");
            secureTracker.commit(signedIds[i], "POST_CHECKOUT");
        }

        for (int i = 0; i < 5; i++) {
            assertEquals("CONFIRM", secureTracker.getCurrentState(signedIds[i]));
        }

        assertEquals(5, secureTracker.getActiveSessionCount());
    }

    // ==========================================
    // 5. Security TESTS (4)
    // ==========================================

    @Test
    @DisplayName("ST_18: Forged session ID should be blocked at HMAC verification")
    void testForgedSessionIdBlocked() {
        // Don't use generateSignedSessionId — use a forged ID
        assertFalse(secureTracker.checkValid("FORGED_ID.fake_signature", "POST_LOGIN"));
    }

    @Test
    @DisplayName("ST_19: Null session ID should be blocked")
    void testNullSessionIdBlocked() {
        assertFalse(secureTracker.checkValid(null, "POST_LOGIN"));
    }

    @Test
    @DisplayName("ST_20: Replay login should be blocked and session destroyed")
    void testReplayLoginBlocked() {
        String signedId = secureTracker.generateSignedSessionId("USER_200");

        secureTracker.commit(signedId, "POST_LOGIN");

        // Try to login again
        assertFalse(secureTracker.checkValid(signedId, "POST_LOGIN"));
        assertEquals("NO_SESSION", secureTracker.getCurrentState(signedId));
    }

    @Test
    @DisplayName("ST_21: Unknown action should be blocked and session destroyed")
    void testUnknownActionBlocked() {
        String signedId = secureTracker.generateSignedSessionId("USER_201");

        secureTracker.commit(signedId, "POST_LOGIN");

        assertFalse(secureTracker.checkValid(signedId, "HACK_ACTION"));
        assertEquals("NO_SESSION", secureTracker.getCurrentState(signedId));
    }

    // ==========================================
    // 6. MAX_SESSIONS TESTS (2)
    // ==========================================

    @Test
    @DisplayName("ST_22: MAX_SESSIONS limit should prevent memory exhaustion")
    void testMaxSessionsLimit() {
        SecureSessionTracker smallTracker = new SecureSessionTracker(3);

        // Create 3 sessions (max)
        for (int i = 1; i <= 3; i++) {
            String id = smallTracker.generateSignedSessionId("USER_" + i);
            assertTrue(smallTracker.checkValid(id, "POST_LOGIN"));
            smallTracker.commit(id, "POST_LOGIN");
        }

        assertEquals(3, smallTracker.getActiveSessionCount());

        // 4th session should be blocked
        String id4 = smallTracker.generateSignedSessionId("USER_4");
        assertFalse(smallTracker.checkValid(id4, "POST_LOGIN"), "Should block when MAX_SESSIONS reached");
    }

    @Test
    @DisplayName("ST_23: Session count decreases after logout")
    void testSessionCountDecreasesAfterLogout() {
        String signedId = secureTracker.generateSignedSessionId("USER_300");

        secureTracker.commit(signedId, "POST_LOGIN");
        assertEquals(1, secureTracker.getActiveSessionCount());

        secureTracker.commit(signedId, "POST_LOGOUT");
        assertEquals(0, secureTracker.getActiveSessionCount());
    }

    // ==========================================
    // 7. Edge Case TESTS (2)
    // ==========================================

    @Test
    @DisplayName("ST_24: Null action should be blocked without crashing")
    void testNullActionBlocked() {
        String signedId = secureTracker.generateSignedSessionId("USER_400");
        assertFalse(secureTracker.checkValid(signedId, null));
    }

    @Test
    @DisplayName("ST_25: Session can be recreated after destruction")
    void testSessionCanBeRecreatedAfterDestruction() {
        String signedId = secureTracker.generateSignedSessionId("USER_500");

        // First session
        secureTracker.commit(signedId, "POST_LOGIN");
        secureTracker.commit(signedId, "POST_CART");

        // Invalid action destroys session
        assertFalse(secureTracker.checkValid(signedId, "POST_CHECKOUT"));
        assertEquals("NO_SESSION", secureTracker.getCurrentState(signedId));

        // Same signed ID can start fresh
        assertTrue(secureTracker.checkValid(signedId, "POST_LOGIN"));
        secureTracker.commit(signedId, "POST_LOGIN");
        assertEquals("AUTH", secureTracker.getCurrentState(signedId));
    }

    public static void main(String[] args) {
        System.out.println("=== Running SessionComprehensiveTest Suite (25 Tests) ===");
        SessionComprehensiveTest suite = new SessionComprehensiveTest();

        java.util.Map<String, Runnable> tests = new java.util.LinkedHashMap<>();
        tests.put("ST_01: Valid signed session ID", () -> suite.testValidSignedSessionId());
        tests.put("ST_02: Forged session ID rejected", () -> suite.testForgedSessionId());
        tests.put("ST_03: Session ID without signature rejected", () -> suite.testSessionIdWithoutSignature());
        tests.put("ST_04: Null session ID rejected", () -> suite.testNullSessionId());
        tests.put("ST_05: Empty session ID rejected", () -> suite.testEmptySessionId());
        tests.put("ST_06: extractRawId extracts correct raw ID", () -> suite.testExtractRawId());
        tests.put("ST_07: New session with POST_LOGIN allowed", () -> suite.testNewSessionLoginAllowed());
        tests.put("ST_08: New session with POST_CART blocked", () -> suite.testNewSessionCartBlocked());
        tests.put("ST_09: New session with POST_PAY blocked", () -> suite.testNewSessionPayBlocked());
        tests.put("ST_10: New session with POST_CHECKOUT blocked", () -> suite.testNewSessionCheckoutBlocked());
        tests.put("ST_11: Full happy path workflow", () -> suite.testFullHappyPath());
        tests.put("ST_12: State-skip attack blocked & destroyed", () -> suite.testStateSkipAttackBlocked());
        tests.put("ST_13: POST_LOGOUT destroys session", () -> suite.testLogoutDestroysSession());
        tests.put("ST_14: Backward navigation works", () -> suite.testBackwardNavigation());
        tests.put("ST_15: Two users independent", () -> suite.testTwoUsersIndependent());
        tests.put("ST_16: Invalid action by User A does not affect B", () -> suite.testInvalidActionDoesNotAffectOtherUser());
        tests.put("ST_17: Multiple users complete workflow simultaneously", () -> suite.testMultipleUsersCompleteWorkflow());
        tests.put("ST_18: Forged session ID blocked at HMAC", () -> suite.testForgedSessionIdBlocked());
        tests.put("ST_19: Null session ID blocked", () -> suite.testNullSessionIdBlocked());
        tests.put("ST_20: Replay login blocked", () -> suite.testReplayLoginBlocked());
        tests.put("ST_21: Unknown action blocked & destroyed", () -> suite.testUnknownActionBlocked());
        tests.put("ST_22: MAX_SESSIONS limit prevents memory exhaustion", () -> suite.testMaxSessionsLimit());
        tests.put("ST_23: Session count decreases after logout", () -> suite.testSessionCountDecreasesAfterLogout());
        tests.put("ST_24: Null action blocked without crashing", () -> suite.testNullActionBlocked());
        tests.put("ST_25: Session can be recreated after destruction", () -> suite.testSessionCanBeRecreatedAfterDestruction());

        int passed = 0;
        int failed = 0;

        for (java.util.Map.Entry<String, Runnable> entry : tests.entrySet()) {
            suite.setUp();
            try {
                entry.getValue().run();
                System.out.println("  PASSED: " + entry.getKey());
                passed++;
            } catch (Throwable t) {
                System.out.println("  FAILED: " + entry.getKey() + " -> " + t.getMessage());
                failed++;
            }
        }

        System.out.println("\n========================================");
        System.out.println("Result: " + passed + " PASSED, " + failed + " FAILED (Total " + (passed + failed) + ")");
        System.out.println("========================================");
    }
}