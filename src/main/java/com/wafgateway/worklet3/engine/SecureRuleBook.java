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
        // 1. FORWARD NAVIGATION (RIGHT)
        // Move right, write new state
        // ==========================================
        initialRules.put(new TransitionKey("START", "POST_LOGIN"), "AUTH:RIGHT");
        initialRules.put(new TransitionKey("AUTH", "POST_CART"), "CART:RIGHT");
        initialRules.put(new TransitionKey("CART", "POST_PAY"), "PAY:RIGHT");
        initialRules.put(new TransitionKey("PAY", "POST_CHECKOUT"), "CONFIRM:RIGHT");

        // ==========================================
        // 2. BACKWARD NAVIGATION (LEFT)
        // Blank current cell, move left
        // newState must be the PREVIOUS state!
        // ==========================================
        initialRules.put(new TransitionKey("CART", "DELETE_CART"), "AUTH:LEFT");
        initialRules.put(new TransitionKey("PAY", "CANCEL_PAY"), "CART:LEFT");

        // ==========================================
        // 3. READ-ONLY ACTIONS (STAY)
        // No tape movement, no state change
        // ==========================================
        initialRules.put(new TransitionKey("AUTH", "GET_CART"), "AUTH:STAY");
        initialRules.put(new TransitionKey("CART", "GET_CART"), "CART:STAY");
        initialRules.put(new TransitionKey("CART", "PUT_CART"), "CART:STAY");
        initialRules.put(new TransitionKey("AUTH", "GET_HOME"), "AUTH:STAY");
        initialRules.put(new TransitionKey("CART", "GET_HOME"), "CART:STAY");
        initialRules.put(new TransitionKey("PAY", "GET_HOME"), "PAY:STAY");
        initialRules.put(new TransitionKey("CONFIRM", "GET_HOME"), "CONFIRM:STAY");
        initialRules.put(new TransitionKey("CONFIRM", "GET_RECEIPT"), "CONFIRM:STAY");

        // Security 1: Make the map immutable
        this.rules = Collections.unmodifiableMap(initialRules);

        // Security 2: Calculate SHA-256 hash
        this.startupHashSHA256 = calculateHash();
    }

    public String getNextState(String currentState, String inputAction) {
        if (!verifyIntegrity()) {
            return "TAMPERED";
        }
        TransitionKey key = new TransitionKey(currentState, inputAction);
        return rules.get(key);
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
            return "HASH_ERROR_" + System.currentTimeMillis();
        }
    }

    private boolean verifyIntegrity() {
        try {
            String currentHash = calculateHash();
            return currentHash.equals(startupHashSHA256);
        } catch (Exception e) {
            return false;
        }
    }
}