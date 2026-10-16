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
            tape.write("V"); // 1. Mark current state as Visited
            tape.moveRight(); // 2. Move read/write head right
            tape.write(newState); // 3. Write the new state securely
            currentState = newState; // 4. Update internal tracking
        }
    }

    public String getCurrentState() {
        return currentState;
    }
}