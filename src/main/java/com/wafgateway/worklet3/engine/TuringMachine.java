package com.wafgateway.worklet3.engine;

import com.wafgateway.worklet3.tape.SecureTape;

public class TuringMachine {
    private SecureTape tape;
    private SecureRuleBook ruleBook;
    private String currentState;

    public TuringMachine() {
        this(new SecureRuleBook());
    }

    public TuringMachine(SecureRuleBook sharedRuleBook) {
        this.tape = new SecureTape();
        this.ruleBook = sharedRuleBook;
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
            String[] parts = newState.split(":");
            String state = parts[0];
            String direction = parts.length > 1 ? parts[1] : "RIGHT";

            switch (direction) {
                case "RIGHT":
                    tape.moveRight();
                    tape.write(state);
                    break;
                case "LEFT":
                    tape.write("B");
                    tape.moveLeft();
                    break;
                case "STAY":
                    // No tape movement
                    break;
            }
            currentState = state;
        }
    }

    public String getCurrentState() {
        return currentState;
    }

    public SecureTape getTape() {
        return tape;
    }

    public SecureRuleBook getRuleBook() {
        return ruleBook;
    }
}