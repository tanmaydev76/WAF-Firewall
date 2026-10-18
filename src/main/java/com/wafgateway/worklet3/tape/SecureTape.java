package com.wafgateway.worklet3.tape;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class SecureTape {
    Tape rawTape;
    private SecretKeySpec secretKey;
    private Cipher cipher;

    public SecureTape() {
        this.rawTape = new Tape();
        // 16-byte AES key (128-bit encryption)
        byte[] keyBytes = "AutomataGuardWAF".getBytes();
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        try {
            this.cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        } catch (Exception e) {
            throw new RuntimeException("Error initializing Cipher", e);
        }
        // Encrypt the initial START state on the raw tape
        this.rawTape.write(encrypt("START"));
    }

    private String encrypt(String data) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Encryption error", e);
        }
    }

    private String decrypt(String encryptedData) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes);
        } catch (Exception e) {
            // SECURITY FEATURE: If decryption fails (attacker modified RAM), return invalid
            // state!
            return "TAMPERED";
        }
    }

    public String read() {
        // Read encrypted data from TapeNode, decrypt it, return plain text
        String encryptedData = rawTape.read();
        return decrypt(encryptedData);
    }

    public void write(String symbol) {
        // Encrypt the plain text, then store the gibberish in the TapeNode
        String encryptedSymbol = encrypt(symbol);
        rawTape.write(encryptedSymbol);
    }

    public void moveRight() {
        rawTape.moveRight();
        if ("B".equals(rawTape.read())) {
            rawTape.write(encrypt("B"));
        }
    }

    public void moveLeft() {
        rawTape.moveLeft();
        if ("B".equals(rawTape.read())) {
            rawTape.write(encrypt("B"));
        }
    }
}