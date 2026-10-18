package com.wafgateway.worklet3.engine;

public class ManualEngineTest {
    public static void main(String[] args) {
        System.out.println("=== Turing Machine Engine Test ===");
        SecureRuleBook ruleBook = new SecureRuleBook();
        TuringMachine tm = new TuringMachine(ruleBook);

        System.out.println("Initial State: " + tm.getCurrentState());

        // TEST 1: Illegal Transition (Trying to PAY from START state)
        System.out.println("\n--- Test 1: Attempting POST_PAY from START (State Skip) ---");
        boolean isValid1 = tm.checkValid("POST_PAY");
        System.out.println("Is valid? " + isValid1); // Should print false
        if (!isValid1) {
            System.out.println("SECURITY PASSED: Blocked illegal state-skip!");
        }

        // TEST 2: Legal Transition (START -> POST_LOGIN -> AUTH)
        System.out.println("\n--- Test 2: Attempting POST_LOGIN from START ---");
        boolean isValid2 = tm.checkValid("POST_LOGIN");
        System.out.println("Is valid? " + isValid2); // Should print true

        if (isValid2) {
            // Simulate backend returning 200 OK, so we commit the tape!
            tm.commit("POST_LOGIN");
            System.out.println("State after commit: " + tm.getCurrentState()); // Should print AUTH
        }

        // TEST 3: Verify Tape Marked 'V' correctly
        System.out.println("\n--- Test 3: Attempting POST_CART from AUTH ---");
        boolean isValid3 = tm.checkValid("POST_CART");
        if (isValid3) {
            tm.commit("POST_CART");
            System.out.println("State after commit: " + tm.getCurrentState()); // Should print CART
        }
    }
}
