package com.wafgateway.worklet3.tape;

// Class holding the defination of the Tape Node with the DLL
public class TapeNode {
    String data;
    TapeNode next;
    TapeNode prev;

    public TapeNode(String data) {
        this.data = data;
        this.next = null;
        this.prev = null;
    }
}