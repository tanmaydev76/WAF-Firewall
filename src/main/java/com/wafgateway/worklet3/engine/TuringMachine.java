package com.wafgateway.worklet3.engine;

import com.wafgateway.worklet3.tape.Tape;

public class TuringMachine {
    private Tape tape;

    public TuringMachine() {
        this.tape = new Tape();
    }

    public TuringMachine(Tape tape) {
        this.tape = tape;
    }

    public int evaluate(String req_action) {
        String currentState = tape.read();
        String nextState = TransitionKey.getNextState(currentState, req_action);

        if (nextState != null) {
            tape.write(nextState);
            tape.moveRight();
            return 1;
        }

        return 0;
    }

    public Tape getTape() {
        return tape;
    }
}