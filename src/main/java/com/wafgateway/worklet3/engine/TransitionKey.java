package com.wafgateway.worklet3.engine;

import java.util.HashMap;

public class TransitionKey {
    private String currentState;
    private String inputAction;

    // The HashMap is stored statically INSIDE the TransitionKey class
    private static HashMap<TransitionKey, String> rules = new HashMap<>();

    // Pre-fill the HashMap at startup
    static {
        rules.put(new TransitionKey("START", "AUTH"), "AUTH");
        rules.put(new TransitionKey("AUTH", "CART"), "CART");
        rules.put(new TransitionKey("CART", "PAY"), "PAY");
        rules.put(new TransitionKey("PAY", "CONFIRM"), "CONFIRM");
    }

    // Constructor
    public TransitionKey(String currentState, String inputAction) {
        this.currentState = currentState;
        this.inputAction = inputAction;
    }

    // Method for TM to access the rules
    public static String getNextState(String state, String action) {
        TransitionKey key = new TransitionKey(state, action);
        return rules.get(key); // Returns null if not found
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        TransitionKey key = (TransitionKey) obj;
        return currentState.equals(key.currentState) && inputAction.equals(key.inputAction);
    }

    @Override
    public int hashCode() {
        return currentState.hashCode() + inputAction.hashCode();
    }
}