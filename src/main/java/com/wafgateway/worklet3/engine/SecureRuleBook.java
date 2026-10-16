package com.wafgateway.worklet3.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.security.MessageDigest;

public class SecureRuleBook {
    private Map<TransitionKey, String> rules;
    private String startupHashSHA256;

    public SecureRuleBook() {
        HashMap<TransitionKey, String> initialRules = new HashMap<>();

        // ==========================================
        // PRE-FILLING ONLY VALID RULES (HTTP Method + Endpoint)
        // ==========================================

        // 1. Authentication Flow
        initialRules.put(new TransitionKey("START", "POST_LOGIN"), "AUTH");

        // 2. Cart Management
        initialRules.put(new TransitionKey("AUTH", "POST_CART"), "CART");
        initialRules.put(new TransitionKey("AUTH", "GET_CART"), "AUTH"); // Allowed, no state change
        initialRules.put(new TransitionKey("CART", "GET_CART"), "CART"); // Allowed, no state change
        initialRules.put(new TransitionKey("CART", "PUT_CART"), "CART"); // Allowed, no state change

        // 3. Checkout Flow (Strictly linear)
        initialRules.put(new TransitionKey("CART", "POST_PAY"), "PAY");
        initialRules.put(new TransitionKey("PAY", "POST_CHECKOUT"), "CONFIRM");

        // Security 1: Make the map immutable
        this.rules = Collections.unmodifiableMap(initialRules);

        // Security 2: Calculate SHA-256 hash
        this.startupHashSHA256 = calculateHash();
    }

    public String getNextState(String currentState, String inputAction) {
        // Security 3: Verify rules haven't been tampered with in RAM
        if (!verifyIntegrity()) {
            return "TAMPERED";
        }

        TransitionKey key = new TransitionKey(currentState, inputAction);
        return rules.get(key); // Returns null if not found (Default Deny)
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String rulesString = rules.toString();
            byte[] hashBytes = digest.digest(rulesString.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 initialization error", e);
        }
    }

    private boolean verifyIntegrity() {
        String currentHash = calculateHash();
        return currentHash.equals(startupHashSHA256);
    }
}