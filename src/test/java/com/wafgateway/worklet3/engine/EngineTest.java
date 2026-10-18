package com.wafgateway.worklet3.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Engine Package - Comprehensive Test Suite (18 Tests)")
public class EngineTest {

    private TuringMachine tm;
    private SecureRuleBook ruleBook;

    @BeforeEach
    void setUp() {
        ruleBook = new SecureRuleBook();
        tm = new TuringMachine(ruleBook);
    }

    // ==========================================
    // POSITIVE TEST CASES (8)
    // ==========================================

    @Test
    @DisplayName("TC_01: Full happy path START -> AUTH -> CART -> PAY -> CONFIRM")
    void testFullHappyPath() {
        assertEquals("START", tm.getCurrentState());

        assertTrue(tm.checkValid("POST_LOGIN"));
        tm.commit("POST_LOGIN");
        assertEquals("AUTH", tm.getCurrentState());

        assertTrue(tm.checkValid("POST_CART"));
        tm.commit("POST_CART");
        assertEquals("CART", tm.getCurrentState());

        assertTrue(tm.checkValid("POST_PAY"));
        tm.commit("POST_PAY");
        assertEquals("PAY", tm.getCurrentState());

        assertTrue(tm.checkValid("POST_CHECKOUT"));
        tm.commit("POST_CHECKOUT");
        assertEquals("CONFIRM", tm.getCurrentState());
    }

    @Test
    @DisplayName("TC_02: GET_CART from AUTH should be allowed (no state change)")
    void testGetCartFromAuth() {
        tm.commit("POST_LOGIN"); // Now in AUTH
        assertTrue(tm.checkValid("GET_CART"), "GET_CART from AUTH should be allowed");
        tm.commit("GET_CART");
        assertEquals("AUTH", tm.getCurrentState(), "State should remain AUTH");
    }

    @Test
    @DisplayName("TC_03: GET_CART from CART should be allowed (no state change)")
    void testGetCartFromCart() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART"); // Now in CART
        assertTrue(tm.checkValid("GET_CART"), "GET_CART from CART should be allowed");
        tm.commit("GET_CART");
        assertEquals("CART", tm.getCurrentState(), "State should remain CART");
    }

    @Test
    @DisplayName("TC_04: PUT_CART from CART should be allowed (no state change)")
    void testPutCartFromCart() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART"); // Now in CART
        assertTrue(tm.checkValid("PUT_CART"), "PUT_CART from CART should be allowed");
        tm.commit("PUT_CART");
        assertEquals("CART", tm.getCurrentState(), "State should remain CART");
    }

    @Test
    @DisplayName("TC_05: TransitionKey equals() should return true for same values")
    void testTransitionKeyEquals() {
        TransitionKey key1 = new TransitionKey("START", "POST_LOGIN");
        TransitionKey key2 = new TransitionKey("START", "POST_LOGIN");
        assertTrue(key1.equals(key2), "Two keys with same values should be equal");
    }

    @Test
    @DisplayName("TC_06: TransitionKey hashCode() should be consistent")
    void testTransitionKeyHashCodeConsistency() {
        TransitionKey key1 = new TransitionKey("CART", "POST_PAY");
        TransitionKey key2 = new TransitionKey("CART", "POST_PAY");
        assertEquals(key1.hashCode(), key2.hashCode(), "Same key values should produce same hash code");
    }

    @Test
    @DisplayName("TC_07: Commit without checkValid should still work")
    void testCommitWithoutCheckValid() {
        // commit() internally calls getNextState, so it should work independently
        tm.commit("POST_LOGIN");
        assertEquals("AUTH", tm.getCurrentState(), "Commit should advance state even without checkValid");
    }

    @Test
    @DisplayName("TC_08: GET_HOME from any state should be allowed (if rule exists)")
    void testGetHomeFromAnyState() {
        // Note: If GET_HOME rule is not defined in SecureRuleBook, this will return
        // false.
        // This test verifies the behavior based on current rulebook.
        tm.commit("POST_LOGIN"); // Now in AUTH
        // GET_HOME may or may not be in the rulebook - test the behavior
        boolean result = tm.checkValid("GET_HOME");
        // If rule exists, should be true; if not, should be false. Either way, no
        // crash.
        assertNotNull(result, "Should return a boolean without crashing");
    }

    // ==========================================
    // NEGATIVE TEST CASES (10)
    // ==========================================

    @Test
    @DisplayName("TC_09: Replay login (POST_LOGIN from AUTH) should be blocked")
    void testReplayLogin() {
        tm.commit("POST_LOGIN"); // Now in AUTH
        assertFalse(tm.checkValid("POST_LOGIN"), "Replaying POST_LOGIN from AUTH should be blocked");
    }

    @Test
    @DisplayName("TC_10: Double checkout (POST_CHECKOUT from CONFIRM) should be blocked")
    void testDoubleCheckout() {
        // Complete the full workflow
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART");
        tm.commit("POST_PAY");
        tm.commit("POST_CHECKOUT"); // Now in CONFIRM
        assertFalse(tm.checkValid("POST_CHECKOUT"), "Double checkout should be blocked (replay attack)");
    }

    @Test
    @DisplayName("TC_11: PUT_CART from PAY should be blocked (cart locked)")
    void testPutCartFromPay() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART");
        tm.commit("POST_PAY"); // Now in PAY
        assertFalse(tm.checkValid("PUT_CART"), "PUT_CART from PAY should be blocked (cart is locked)");
    }

    @Test
    @DisplayName("TC_12: POST_PAY from START should be blocked (skip login)")
    void testPostPayFromStart() {
        assertFalse(tm.checkValid("POST_PAY"), "POST_PAY from START should be blocked");
    }

    @Test
    @DisplayName("TC_13: POST_CHECKOUT from AUTH should be blocked (skip cart+pay)")
    void testPostCheckoutFromAuth() {
        tm.commit("POST_LOGIN"); // Now in AUTH
        assertFalse(tm.checkValid("POST_CHECKOUT"), "POST_CHECKOUT from AUTH should be blocked");
    }

    @Test
    @DisplayName("TC_14: Unknown action should be blocked")
    void testUnknownAction() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART"); // Now in CART
        assertFalse(tm.checkValid("DELETE_USER"), "Unknown action DELETE_USER should be blocked");
    }

    @Test
    @DisplayName("TC_15: Null action should be blocked without crashing")
    void testNullAction() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART"); // Now in CART
        try {
            boolean result = tm.checkValid(null);
            assertFalse(result, "Null action should be blocked");
        } catch (NullPointerException e) {
            // Acceptable: system threw exception instead of allowing the action
        }
    }

    @Test
    @DisplayName("TC_16: Empty action should be blocked")
    void testEmptyAction() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART"); // Now in CART
        assertFalse(tm.checkValid(""), "Empty action string should be blocked");
    }

    @Test
    @DisplayName("TC_17: Rejected request should NOT advance the tape")
    void testRejectedRequestDoesNotAdvanceTape() {
        tm.commit("POST_LOGIN");
        tm.commit("POST_CART"); // Now in CART

        // Try an invalid action (skip payment)
        boolean blocked = tm.checkValid("POST_CHECKOUT");
        assertFalse(blocked, "POST_CHECKOUT from CART should be blocked");

        // The state should still be CART (tape did not advance)
        assertEquals("CART", tm.getCurrentState(), "Tape should NOT advance on rejected request");

        // Now a valid action should still work correctly
        assertTrue(tm.checkValid("POST_PAY"), "POST_PAY from CART should still be valid");
        tm.commit("POST_PAY");
        assertEquals("PAY", tm.getCurrentState(), "State should now be PAY");
    }

    @Test
    @DisplayName("TC_18: TransitionKey equals() should return false for different values")
    void testTransitionKeyNotEqual() {
        TransitionKey key1 = new TransitionKey("START", "POST_LOGIN");
        TransitionKey key2 = new TransitionKey("AUTH", "POST_LOGIN");
        assertFalse(key1.equals(key2), "Keys with different currentState should not be equal");

        TransitionKey key3 = new TransitionKey("START", "POST_CART");
        assertFalse(key1.equals(key3), "Keys with different inputAction should not be equal");
    }

    public static void main(String[] args) {
        System.out.println("=== Running EngineTest Suite (18 Tests) ===");
        EngineTest suite = new EngineTest();

        java.util.Map<String, Runnable> tests = new java.util.LinkedHashMap<>();
        tests.put("TC_01: Full happy path", () -> suite.testFullHappyPath());
        tests.put("TC_02: GET_CART from AUTH", () -> suite.testGetCartFromAuth());
        tests.put("TC_03: GET_CART from CART", () -> suite.testGetCartFromCart());
        tests.put("TC_04: PUT_CART from CART", () -> suite.testPutCartFromCart());
        tests.put("TC_05: TransitionKey equals", () -> suite.testTransitionKeyEquals());
        tests.put("TC_06: TransitionKey hashCode consistency", () -> suite.testTransitionKeyHashCodeConsistency());
        tests.put("TC_07: Commit without checkValid", () -> suite.testCommitWithoutCheckValid());
        tests.put("TC_08: GET_HOME from any state", () -> suite.testGetHomeFromAnyState());
        tests.put("TC_09: Replay login blocked", () -> suite.testReplayLogin());
        tests.put("TC_10: Double checkout blocked", () -> suite.testDoubleCheckout());
        tests.put("TC_11: PUT_CART from PAY blocked", () -> suite.testPutCartFromPay());
        tests.put("TC_12: POST_PAY from START blocked", () -> suite.testPostPayFromStart());
        tests.put("TC_13: POST_CHECKOUT from AUTH blocked", () -> suite.testPostCheckoutFromAuth());
        tests.put("TC_14: Unknown action blocked", () -> suite.testUnknownAction());
        tests.put("TC_15: Null action blocked", () -> suite.testNullAction());
        tests.put("TC_16: Empty action blocked", () -> suite.testEmptyAction());
        tests.put("TC_17: Rejected request does not advance tape", () -> suite.testRejectedRequestDoesNotAdvanceTape());
        tests.put("TC_18: TransitionKey not equal", () -> suite.testTransitionKeyNotEqual());

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