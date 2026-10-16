package com.wafgateway.worklet3.tape;

public class ManualTapeTest {
    public static void main(String[] args) {
        System.out.println("=== 1. Basic Tape (DLL) Test ===");
        Tape tape = new Tape();
        System.out.println("Initial State: " + tape.read()); // Should print START

        // Simulate START -> AUTH transition
        tape.write("V"); // Mark START as Visited
        tape.moveRight(); // Move head right (creates blank node)
        tape.write("AUTH"); // Write new state
        System.out.println("State after Login: " + tape.read()); // Should print AUTH

        // Simulate AUTH -> CART transition
        tape.write("V");
        tape.moveRight();
        tape.write("CART");
        System.out.println("State after Cart: " + tape.read()); // Should print CART

        // Prove DLL moves backward
        tape.moveLeft();
        System.out.println("Move Left 1 step: " + tape.read()); // Should print V
        tape.moveRight();
        System.out.println("Move Right 1 step: " + tape.read()); // Should print CART

        System.out.println("\n=== 2. SecureTape (AES Encryption) Test ===");
        SecureTape secureTape = new SecureTape();
        System.out.println("Secure Initial State: " + secureTape.read()); // Should print START

        // Write "PAY" securely
        secureTape.write("V");
        secureTape.moveRight();
        secureTape.write("PAY");
        System.out.println("Secure State (Decrypted on read): " + secureTape.read()); // Should print PAY

        System.out.println("\n=== 3. Memory Tamper Simulation Test ===");
        // NOTE: For this to work, change 'private Tape rawTape;' to 'Tape rawTape;' in
        // SecureTape.java

        // Attacker dumps RAM and injects a fake string directly into the TapeNode
        secureTape.rawTape.write("HACKED_MEMORY_STRING");

        // When the TM tries to read, decryption will fail
        System.out.println("Reading after tampering...");
        String result = secureTape.read();
        System.out.println("Result: " + result); // Should print TAMPERED
        if (result.equals("TAMPERED")) {
            System.out.println("SECURITY CHECK PASSED: System safely rejected tampered memory!");
        } else {
            System.out.println("SECURITY CHECK FAILED: System was hacked!");
        }
        System.out.println("\n=== 4. Verify Raw Memory is Gibberish (TC_07) ===");
        // Create a fresh SecureTape to prove the raw memory holds encrypted text
        SecureTape secureTape2 = new SecureTape();
        secureTape2.write("V");
        secureTape2.moveRight();

        String plainText = "PAY";
        secureTape2.write(plainText); // Write plain text

        // 1. Read via SecureTape (should be decrypted)
        System.out.println("Plain Text Read via SecureTape.read(): " + secureTape2.read());

        // 2. Read directly from raw TapeNode memory (should be encrypted gibberish)
        String rawMemoryData = secureTape2.rawTape.read();
        System.out.println("Raw Memory Data via rawTape.read():   " + rawMemoryData);

        // Prove they are not the same
        if (!rawMemoryData.equals(plainText)) {
            System.out
                    .println("SECURITY CHECK PASSED: The raw memory does not contain the plain text! It is encrypted.");
        } else {
            System.out.println("SECURITY CHECK FAILED: The plain text is sitting in memory!");
        }
    }
}