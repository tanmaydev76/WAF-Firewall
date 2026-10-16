package com.wafgateway.worklet3.tape;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tape Package - Comprehensive Test Suite (15 Tests)")
public class TapeTest {

    private Tape tape;
    private SecureTape secureTape;

    @BeforeEach
    void setUp() {
        tape = new Tape();
        secureTape = new SecureTape();
    }

    // ==========================================
    // POSITIVE TEST CASES (8)
    // ==========================================

    @Test
    @DisplayName("TC_01: Initial tape state should be START")
    void testInitialStateIsStart() {
        assertEquals("START", tape.read(), "Tape should initialize with START state");
    }

    @Test
    @DisplayName("TC_02: Write should overwrite current node data")
    void testWriteOverwritesCurrentNode() {
        tape.write("V");
        assertEquals("V", tape.read(), "Tape should read the newly written value");
    }

    @Test
    @DisplayName("TC_03: moveRight should create dynamic Blank node")
    void testMoveRightCreatesBlankNode() {
        tape.moveRight();
        assertEquals("B", tape.read(), "Moving right should create/read a Blank node");
    }

    @Test
    @DisplayName("TC_04: moveLeft should create dynamic Blank node")
    void testMoveLeftCreatesBlankNode() {
        tape.moveLeft();
        assertEquals("B", tape.read(), "Moving left should create/read a Blank node");
    }

    @Test
    @DisplayName("TC_05: Round-trip navigation (right then left returns to original cell)")
    void testRoundTripNavigation() {
        tape.write("V");
        tape.moveRight();
        tape.write("AUTH");
        tape.moveLeft();
        assertEquals("V", tape.read(), "Moving back left should return to the original cell with V");
    }

    @Test
    @DisplayName("TC_06: Multiple consecutive writes preserve all cell data")
    void testMultipleConsecutiveWrites() {
        tape.write("V");
        tape.moveRight();
        tape.write("AUTH");
        tape.moveRight();
        tape.write("V");

        // Navigate back to verify all cells are intact
        tape.moveLeft();
        assertEquals("AUTH", tape.read(), "Cell 2 should contain AUTH");
        tape.moveLeft();
        assertEquals("V", tape.read(), "Cell 1 should contain V");
    }

    @Test
    @DisplayName("TC_07: SecureTape encrypts and decrypts long strings correctly")
    void testSecureTapeWithLongStrings() {
        secureTape.write("V");
        secureTape.moveRight();
        secureTape.write("CONFIRM");
        assertEquals("CONFIRM", secureTape.read(), "Long string should decrypt correctly");
    }

    @Test
    @DisplayName("TC_08: SecureTape encryption is transparent to the caller")
    void testSecureTapeEncryptionTransparency() {
        secureTape.write("PAY");
        assertEquals("PAY", secureTape.read(), "Decrypted output must match original input");
    }

    // ==========================================
    // NEGATIVE TEST CASES (7)
    // ==========================================

    @Test
    @DisplayName("TC_09: SecureTape write null should not crash the system")
    void testSecureTapeWriteNull() {
        // Writing null should be handled gracefully, not crash
        try {
            secureTape.write(null);
            // If it throws an exception, that's acceptable behavior too
            // The test passes as long as the JVM doesn't crash
        } catch (RuntimeException e) {
            // Acceptable: system threw an exception instead of crashing
            assertNotNull(e.getMessage(), "Exception should have a message");
        }
    }

    @Test
    @DisplayName("TC_10: SecureTape write empty string should be handled")
    void testSecureTapeWriteEmptyString() {
        try {
            secureTape.write("");
            // If it doesn't crash, test passes
        } catch (RuntimeException e) {
            // Acceptable: system rejected empty string
            assertNotNull(e.getMessage(), "Exception should have a message");
        }
    }

    @Test
    @DisplayName("TC_11: Tampering with a single cell should return TAMPERED")
    void testTamperDetectionSingleCell() {
        secureTape.write("CART");
        // Simulate attacker injecting a raw string into the underlying TapeNode
        secureTape.rawTape.write("HACKED_MEMORY_STRING");
        assertEquals("TAMPERED", secureTape.read(), "Must return TAMPERED if memory was modified");
    }

    @Test
    @DisplayName("TC_12: Tampering with multiple cells should return TAMPERED")
    void testTamperDetectionMultipleCells() {
        secureTape.write("V");
        secureTape.moveRight();
        secureTape.write("PAY");

        // Tamper with the current cell
        secureTape.rawTape.write("HACKED_1");
        String result1 = secureTape.read();
        assertEquals("TAMPERED", result1, "First tampered cell should return TAMPERED");

        // Move left and tamper with the previous cell
        secureTape.moveLeft();
        secureTape.rawTape.write("HACKED_2");
        String result2 = secureTape.read();
        assertEquals("TAMPERED", result2, "Second tampered cell should return TAMPERED");
    }

    @Test
    @DisplayName("TC_13: Extreme tape growth (100 moveRight) should not crash")
    void testExtremeTapeGrowth() {
        // Move right 100 times to test memory stability
        for (int i = 0; i < 100; i++) {
            tape.moveRight();
            tape.write("V");
        }
        assertEquals("V", tape.read(), "After 100 moves, the head should still read the last written value");
    }

    @Test
    @DisplayName("TC_14: Raw memory should NOT contain plaintext (encryption verification)")
    void testRawMemoryContainsOnlyGibberish() {
        secureTape.write("V");
        secureTape.moveRight();
        secureTape.write("PAY");

        String rawMemory = secureTape.rawTape.read();
        assertNotEquals("PAY", rawMemory, "Raw memory must NOT contain the plaintext");
        assertNotEquals("", rawMemory, "Raw memory must not be empty");
    }

    @Test
    @DisplayName("TC_15: moveLeft from START then moveRight should return to START")
    void testMoveLeftThenRightReturnsToStart() {
        // Move left (creates a blank node)
        tape.moveLeft();
        assertEquals("B", tape.read(), "Left of START should be Blank");

        // Move back right to START
        tape.moveRight();
        assertEquals("START", tape.read(), "Moving right from left-blank should return to START");
    }
}