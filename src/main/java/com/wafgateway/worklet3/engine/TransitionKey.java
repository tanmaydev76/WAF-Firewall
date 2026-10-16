package com.wafgateway.worklet3.engine;

public class TransitionKey {
    private String currentState;
    private String inputAction;

    public TransitionKey(String currentState, String inputAction) {
        this.currentState = currentState;
        this.inputAction = inputAction;
    }

    // Required for HashMap to compare keys correctly
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        TransitionKey key = (TransitionKey) obj;
        return currentState.equals(key.currentState) && inputAction.equals(key.inputAction);
    }

    // Required for HashMap to hash the key for O(1) lookup
    @Override
    public int hashCode() {
        return currentState.hashCode() + inputAction.hashCode();
    }
}