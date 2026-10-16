package com.wafgateway.worklet3.engine;

import com.wafgateway.worklet3.tape.SecureTape;

public class TuringMachine {
    private SecureTape tape;
    private SecureRuleBook ruleBook;
    private String currentState;

    public TuringMachine() {
        this.tape = new SecureTape();
        this.ruleBook = new SecureRuleBook();
        this.currentState = "START";
    }

    // Phase 1: Check if the transition is mathematically legal (Does NOT move tape)
    public synchronized boolean checkValid(String inputAction) {
        String newState = ruleBook.getNextState(currentState, inputAction);

        // If the rule exists and the rulebook wasn't tampered with, it's valid
        if (newState != null && !newState.equals("TAMPERED")) {
            return true;
        }
        return false;
    }

    // Phase 2: Commit the transition to the tape (Moves tape and updates state)
    public synchronized void commit(String inputAction) {
        String newState = ruleBook.getNextState(currentState, inputAction);

        if (newState != null && !newState.equals("TAMPERED")) {
            // Only write/move if state actually changes (Optimization)
            if (!newState.equals(currentState)) {
                tape.write("V");
                tape.moveRight();
                tape.write(newState);
            }
            currentState = newState;
        }
    }

    public String getCurrentState() {
        return currentState;
    }
}