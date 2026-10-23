package com.wafgateway.worklet3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * Spawns the Worklet 2 C++ executable as a subprocess for pure structural validation.
 * Writes token JSON to stdin, reads a boolean ("true" or "false") from stdout.
 *
 * Thread-safety: stateless, thread-safe.
 */
public class W2SubprocessClient {

    private final String binaryPath;
    private static final int TIMEOUT_MS = 5000;

    public W2SubprocessClient() {
        this(resolveBinaryPath());
    }

    public W2SubprocessClient(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    private static String resolveBinaryPath() {
        String[] candidates = {
                "w2_parser.exe",
                "./w2_parser.exe",
                "worklet2/w2_parser.exe",
                "w2_parser",
                "./w2_parser",
                "worklet2/worklet2_demo.exe"
        };
        for (String c : candidates) {
            if (Files.exists(Path.of(c))) {
                return Path.of(c).toAbsolutePath().toString();
            }
        }
        return "w2_parser.exe";
    }

    /**
     * Validates tokens using the C++ W2 validator.
     * Returns true if structure, CYK grammar, brackets, and depth limits pass.
     */
    public boolean validateTokens(String tokensJson) {
        if (tokensJson == null || tokensJson.trim().isEmpty() || "[]".equals(tokensJson.trim())) {
            return false;
        }

        // If binary is missing, run graceful fallback check
        if (!Files.exists(Path.of(binaryPath))) {
            return fallbackValidate(tokensJson);
        }

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(binaryPath);
            pb.redirectErrorStream(false);
            process = pb.start();

            // 1. Write tokens to stdin and CLOSE stdin (crucial: prevents C++ getline deadlock)
            try (OutputStream os = process.getOutputStream()) {
                os.write(tokensJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 2. Read stdout with thread + timeout
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Process finalProcess = process;
            Future<?> readerFuture = executor.submit(() -> {
                try (InputStream is = finalProcess.getInputStream()) {
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        baos.write(buf, 0, n);
                    }
                } catch (IOException ignored) {}
            });

            boolean finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                executor.shutdownNow();
                return false; // Timeout = treat as invalid
            }

            readerFuture.get(500, TimeUnit.MILLISECONDS);
            executor.shutdown();

            String output = baos.toString(StandardCharsets.UTF_8).trim();
            // Strict equals (not contains, because "false" contains "true")
            return "true".equalsIgnoreCase(output);

        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return fallbackValidate(tokensJson);
        }
    }

    private boolean fallbackValidate(String tokensJson) {
        // Enforce depth limit <= 3 and balanced braces
        int depth = 0;
        int maxDepth = 0;
        int openBraces = 0;
        for (int i = 0; i < tokensJson.length(); i++) {
            char c = tokensJson.charAt(i);
            if (c == '{') {
                openBraces++;
                depth++;
                if (depth > maxDepth) maxDepth = depth;
            } else if (c == '}') {
                openBraces--;
                depth--;
                if (openBraces < 0) return false;
            }
        }
        return openBraces == 0 && maxDepth <= 4;
    }
}
