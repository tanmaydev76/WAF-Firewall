package com.wafgateway.worklet3.tape;

public class Tape {
    private TapeNode currentNode;
    private int length;
    private final int MAX_LENGTH = 50;

    public Tape() {
        // Start with ONLY the START node.
        this.currentNode = new TapeNode("START");
        this.length = 1;
    }

    public String read() {
        return this.currentNode.data;
    }

    public void write(String data) {
        this.currentNode.data = data;
    }

    public void moveRight() {
        if (this.currentNode.next == null) {
            // Dynamically create a Blank node only when we need to move right
            TapeNode newNode = new TapeNode("B");
            newNode.prev = this.currentNode;
            this.currentNode.next = newNode;
            this.length++;
        }
        this.currentNode = this.currentNode.next;
    }

    public void moveLeft() {
        if (this.currentNode.prev == null) {
            // Dynamically create a Blank node only when we need to move left
            TapeNode newNode = new TapeNode("B");
            newNode.next = this.currentNode;
            this.currentNode.prev = newNode;
            this.length++;
        }
        this.currentNode = this.currentNode.prev;
    }

    public int getLength() {
        return this.length;
    }

    public int getMaxLength() {
        return this.MAX_LENGTH;
    }
}