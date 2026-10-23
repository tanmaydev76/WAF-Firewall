package com.wafgateway.worklet3;

import com.wafgateway.worklet3.engine.SecureRuleBook;
import com.wafgateway.worklet3.engine.TuringMachine;
import com.wafgateway.worklet3.session.SecureSessionTracker;
import com.wafgateway.worklet3.session.SessionTokenVerifier;
import com.wafgateway.worklet3.session.SessionTracker;
import com.wafgateway.worklet3.tape.SecureTape;
import com.wafgateway.worklet3.tape.Tape;

import java.util.List;
import java.util.Scanner;

/**
 * End-to-End Interactive Driver for WAF Worklet 3.
 *
 * Demonstrates and invokes all layers of Worklet 3:
 * 1. Cryptographic Token Verification (SessionTokenVerifier - HMAC-SHA256)
 * 2. Session Management & Concurrency (SecureSessionTracker / SessionTracker - ConcurrentHashMap)
 * 3. Automata State Verification (TuringMachine & SecureRuleBook - SHA-256 Rule Hash)
 * 4. Encrypted Memory & Tape Execution (SecureTape - AES-128 & Doubly Linked List)
 */
public class Worklet3Driver {

    private final SecureSessionTracker secureTracker;
    private final SessionTracker baseTracker;
    private final SessionTokenVerifier tokenVerifier;

    public Worklet3Driver() {
        this.secureTracker = new SecureSessionTracker();
        this.baseTracker = this.secureTracker.getSessionTracker();
        this.tokenVerifier = this.secureTracker.getTokenVerifier();
    }

    public static void main(String[] args) {
        Worklet3Driver driver = new Worklet3Driver();
        driver.runInteractiveConsole();
    }

    /**
     * Interactive CLI loop allowing user input of Session ID and Action.
     */
    public void runInteractiveConsole() {
        Scanner scanner = new Scanner(System.in);

        printBanner();

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select an option [1-9]: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleManualInspection(scanner);
                    break;
                case "2":
                    runPresetHappyPath();
                    break;
                case "3":
                    runPresetStateSkipAttack();
                    break;
                case "4":
                    runPresetForgedTokenAttack();
                    break;
                case "5":
                    runPresetBackwardNavigation();
                    break;
                case "6":
                    runPresetMultiUserIsolation();
                    break;
                case "7":
                    displayActiveSessions();
                    break;
                case "8":
                    handleInspectTape(scanner);
                    break;
                case "9":
                case "exit":
                case "quit":
                    System.out.println("\nExiting Worklet 3 Driver. Stay secure!\n");
                    running = false;
                    break;
                default:
                    System.out.println("(!) Invalid option. Please choose between 1 and 9.");
            }

            if (running) {
                System.out.println("\n------------------------------------------------------------");
            }
        }
    }

    /**
     * Interactively prompts user for Session ID and Action, then invokes all Worklet 3 layers.
     * Supports stepping through multiple consecutive actions for the same session.
     */
    private void handleManualInspection(Scanner scanner) {
        System.out.println("\n=== [OPTION 1: MANUAL REQUEST INSPECTION] ===");

        // 1. Session ID input
        System.out.print("Enter Raw Session ID (e.g., USER_101) [default: USER_101]: ");
        String rawInput = scanner.nextLine().trim();
        if (rawInput.isEmpty()) {
            rawInput = "USER_101";
        }

        // 2. Token signing option (test valid signature vs tampered signature)
        System.out.print("Generate valid HMAC signature automatically? (Y/n) [default: Y]: ");
        String signChoice = scanner.nextLine().trim();

        String signedSessionId;
        if (signChoice.equalsIgnoreCase("n")) {
            System.out.print("Enter full signed session ID string (e.g. USER_101.fake_signature): ");
            signedSessionId = scanner.nextLine().trim();
            if (signedSessionId.isEmpty()) {
                signedSessionId = rawInput + ".fake_signature";
            }
        } else {
            signedSessionId = tokenVerifier.generateSignedId(rawInput);
            System.out.println("-> Generated HMAC signed token: " + signedSessionId);
        }

        // 3. Continuous action stepping loop
        boolean continueSession = true;
        while (continueSession) {
            TuringMachine tm = baseTracker.getSessionMachine(rawInput);
            String currState = (tm != null) ? tm.getCurrentState() : "START";

            // Suggest the next natural action in the workflow
            String defaultAction;
            switch (currState) {
                case "START":
                    defaultAction = "POST_LOGIN";
                    break;
                case "AUTH":
                    defaultAction = "POST_CART";
                    break;
                case "CART":
                    defaultAction = "POST_PAY";
                    break;
                case "PAY":
                    defaultAction = "POST_CHECKOUT";
                    break;
                default:
                    defaultAction = "GET_HOME";
                    break;
            }

            System.out.println("\nSession '" + rawInput + "' is currently in State: [" + currState + "]");
            System.out.println("Available Actions: POST_LOGIN, POST_CART, GET_CART, PUT_CART, POST_PAY, POST_CHECKOUT, GET_HOME, CANCEL_PAY, DELETE_CART, POST_LOGOUT");
            System.out.print("Enter HTTP Action [default: " + defaultAction + "]: ");
            String action = scanner.nextLine().trim();
            if (action.isEmpty()) {
                action = defaultAction;
            }

            // 4. Invoke all layers of Worklet 3
            boolean success = processRequest(signedSessionId, action, true);

            // If session failed/destroyed or logged out, stop
            if (!success || "POST_LOGOUT".equals(action) || baseTracker.getSessionMachine(rawInput) == null) {
                break;
            }

            // Check if full flow is complete
            TuringMachine updatedTm = baseTracker.getSessionMachine(rawInput);
            if (updatedTm != null && "CONFIRM".equals(updatedTm.getCurrentState())) {
                System.out.println("\n*** SUCCESS: Full e-commerce cycle completed for session '" + rawInput + "'! ***");
            }

            System.out.print("\nSend next action for session '" + rawInput + "'? (Y/n) [default: Y]: ");
            String nextChoice = scanner.nextLine().trim();
            if (nextChoice.equalsIgnoreCase("n")) {
                continueSession = false;
            }
        }
    }

    /**
     * Interactively prompts to inspect full tape content of any active session.
     */
    private void handleInspectTape(Scanner scanner) {
        System.out.println("\n=== [OPTION 8: INSPECT ENCRYPTED TAPE CONTENT] ===");
        if (secureTracker.getActiveSessionCount() == 0) {
            System.out.println("No active sessions currently tracked. Run a request or preset first (e.g., Option 1 or 2).");
            return;
        }

        java.util.List<String> sessionList = new java.util.ArrayList<>(baseTracker.getSessions().keySet());
        System.out.println("Active Sessions Available:");
        for (int i = 0; i < sessionList.size(); i++) {
            String id = sessionList.get(i);
            TuringMachine tm = baseTracker.getSessionMachine(id);
            String state = (tm != null) ? tm.getCurrentState() : "UNKNOWN";
            System.out.println("  [" + (i + 1) + "] " + id + " (Current State: " + state + ")");
        }

        String defaultTarget = sessionList.get(sessionList.size() - 1);
        System.out.print("\nEnter Session ID or Number [1-" + sessionList.size() + "] [default: " + defaultTarget + "]: ");
        String inputId = scanner.nextLine().trim();
        if (inputId.isEmpty()) {
            inputId = defaultTarget;
        }

        // Support selecting by list number (e.g. 1, 2)
        String rawId = inputId;
        try {
            int num = Integer.parseInt(inputId);
            if (num >= 1 && num <= sessionList.size()) {
                rawId = sessionList.get(num - 1);
            }
        } catch (NumberFormatException ignored) {
            // User typed session ID string
        }

        // Extract raw ID if full signed token was entered
        String extracted = tokenVerifier.extractRawId(rawId);
        if (extracted != null) {
            rawId = extracted;
        }

        TuringMachine tm = baseTracker.getSessionMachine(rawId);
        if (tm == null) {
            System.out.println("(!) Session '" + rawId + "' not found in active session table.");
            return;
        }

        printTapeVisualization(tm, rawId);
    }

    /**
     * Invokes all layers of Worklet 3 step-by-step and displays detailed diagnostics.
     *
     * @param signedSessionId Format: rawId.signature
     * @param action          HTTP action (e.g. POST_LOGIN, POST_CART)
     * @param autoCommit      If true and Phase 1 passes, simulate backend 200 OK and commit
     * @return true if request was allowed and successfully committed, false if blocked
     */
    public boolean processRequest(String signedSessionId, String action, boolean autoCommit) {
        System.out.println("\n============================================================");
        System.out.println(">>> INCOMING REQUEST INSPECTION");
        System.out.println(">>> Token  : " + signedSessionId);
        System.out.println(">>> Action : " + action);
        System.out.println("============================================================");

        // -------------------------------------------------------------
        // LAYER 1: SessionTokenVerifier (HMAC-SHA256 Cryptography)
        // -------------------------------------------------------------
        System.out.println("\n[LAYER 1: CRYPTOGRAPHIC SIGNATURE VERIFICATION]");
        System.out.println("  * Component  : SessionTokenVerifier (HMAC-SHA256)");
        boolean isSignatureValid = tokenVerifier.verifySignature(signedSessionId);
        String rawId = tokenVerifier.extractRawId(signedSessionId);

        if (!isSignatureValid) {
            System.out.println("  * Status     : [ALERT] INVALID OR TAMPERED HMAC SIGNATURE!");
            System.out.println("  * WAF Action : DROP REQUEST (HTTP 403 Forbidden)");
            System.out.println("  * Security   : Request rejected BEFORE touching memory or state machine.");
            return false;
        }

        System.out.println("  * Status     : [PASSED] Legitimate HMAC-SHA256 signature verified.");
        System.out.println("  * Raw ID     : " + rawId);

        // -------------------------------------------------------------
        // LAYER 2: SessionTracker (Concurrency & Map Management)
        // -------------------------------------------------------------
        System.out.println("\n[LAYER 2: SESSION TRACKER & CONCURRENCY MANAGER]");
        System.out.println("  * Component  : SessionTracker (ConcurrentHashMap)");
        TuringMachine tm = baseTracker.getSessionMachine(rawId);
        boolean isNewSession = (tm == null);

        if (isNewSession) {
            System.out.println("  * Session    : NEW SESSION (Not yet in active session table)");
            System.out.println("  * Rule Check : First-action-login policy requires POST_LOGIN");
        } else {
            System.out.println("  * Session    : EXISTING SESSION FOUND");
            System.out.println("  * State Before: " + tm.getCurrentState());
        }

        // -------------------------------------------------------------
        // LAYER 3: TuringMachine & SecureRuleBook (Automata Rule Engine)
        // -------------------------------------------------------------
        System.out.println("\n[LAYER 3: TURING MACHINE & AUTOMATA ENGINE]");
        System.out.println("  * Component  : TuringMachine & SecureRuleBook");

        // Phase 1 Check: Does rule exist from current state?
        boolean isValidTransition = secureTracker.checkValid(signedSessionId, action);

        // Fetch TuringMachine (if it was created during checkValid)
        tm = baseTracker.getSessionMachine(rawId);

        if (!isValidTransition) {
            System.out.println("  * Status     : [BLOCKED] ILLEGAL TRANSITION / ATTACK DETECTED!");
            System.out.println("  * Rule Check : Action '" + action + "' is NOT allowed from current state.");
            System.out.println("  * Defense    : Default Deny policy enforced.");
            System.out.println("  * Penalty    : Session destroyed from memory to neutralize attack.");
            System.out.println("  * Active Sessions: " + secureTracker.getActiveSessionCount());
            return false;
        }

        System.out.println("  * Status     : [APPROVED] Legal transition allowed by RuleBook.");
        if (tm != null) {
            System.out.println("  * Machine State: Current=" + tm.getCurrentState());
        }

        // Special case: POST_LOGOUT
        if ("POST_LOGOUT".equals(action)) {
            System.out.println("  * Action Type: LOGOUT");
            secureTracker.commit(signedSessionId, action);
            System.out.println("  * Result     : Session successfully destroyed upon logout.");
            System.out.println("  * Active Sessions: " + secureTracker.getActiveSessionCount());
            return true;
        }

        // -------------------------------------------------------------
        // LAYER 4: SecureTape & Tape (Encrypted Doubly Linked List)
        // -------------------------------------------------------------
        if (autoCommit) {
            System.out.println("\n[LAYER 4: BACKEND HTTP 200 OK & PHASE 2 COMMIT]");
            System.out.println("  * Component  : SecureTape (AES-128 Encryption & TapeNode DLL)");

            // Commit the transaction to the tape
            secureTracker.commit(signedSessionId, action);

            // Re-fetch TuringMachine and SecureTape for introspection
            tm = baseTracker.getSessionMachine(rawId);
            if (tm != null) {
                printTapeVisualization(tm, rawId);
            }
        }

        System.out.println("\n[SUMMARY]");
        System.out.println("  * Current State of " + rawId + ": " + secureTracker.getCurrentState(signedSessionId));
        System.out.println("  * Total Active Sessions in WAF : " + secureTracker.getActiveSessionCount());
        return true;
    }

    /**
     * Displays a formatted visual diagram and encrypted RAM dump of the Turing Machine tape.
     */
    public void printTapeVisualization(TuringMachine tm, String rawSessionId) {
        if (tm == null) {
            System.out.println("  (!) No Turing Machine found for session: " + rawSessionId);
            return;
        }

        SecureTape secureTape = tm.getTape();
        List<SecureTape.TapeCellSnapshot> snapshots = secureTape.getTapeSnapshots();

        System.out.println("\n  ================ [ENCRYPTED TAPE VISUALIZATION: " + rawSessionId + "] ================");
        System.out.println("  Current State: " + tm.getCurrentState() + " | Total Allocated Cells: " + snapshots.size());
        System.out.println();

        // 1. ASCII Tape Cells Box Representation
        StringBuilder topBorder = new StringBuilder("  ");
        StringBuilder cellNames = new StringBuilder("  ");
        StringBuilder cellValues = new StringBuilder("  ");
        StringBuilder cellStatus = new StringBuilder("  ");
        StringBuilder bottomBorder = new StringBuilder("  ");

        for (SecureTape.TapeCellSnapshot snap : snapshots) {
            String label = "Cell " + snap.index;
            String val = snap.plainText;
            String status = snap.isHead ? "[HEAD] *" : (val.equals("V") ? "(Visited)" : "");

            int width = Math.max(Math.max(label.length(), val.length()), status.length()) + 4;
            width = Math.max(width, 12);

            topBorder.append("+").append("-".repeat(width));
            cellNames.append("|").append(center(label, width));
            cellValues.append("|").append(center(val, width));
            cellStatus.append("|").append(center(status, width));
            bottomBorder.append("+").append("-".repeat(width));
        }
        topBorder.append("+");
        cellNames.append("|");
        cellValues.append("|");
        cellStatus.append("|");
        bottomBorder.append("+");

        System.out.println(topBorder);
        System.out.println(cellNames);
        System.out.println(topBorder);
        System.out.println(cellValues);
        System.out.println(cellStatus);
        System.out.println(bottomBorder);

        // 2. Cryptographic RAM Memory Dump
        System.out.println("\n  --- [CRYPTOGRAPHIC RAM INSPECTION (AES-128)] ---");
        for (SecureTape.TapeCellSnapshot snap : snapshots) {
            String headIndicator = snap.isHead ? " <--- [R/W HEAD CURRENT]" : "";
            System.out.printf("  [Cell %02d] PlainText: %-8s | AES-128 CipherText: %-26s%s\n",
                    snap.index, "\"" + snap.plainText + "\"", "[" + snap.cipherText + "]", headIndicator);
        }
        System.out.println("  ========================================================================");
    }

    private String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text;
        int left = (width - text.length()) / 2;
        int right = width - text.length() - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    // =========================================================================
    // PRESET DEMO SCENARIOS
    // =========================================================================

    private void runPresetHappyPath() {
        System.out.println("\n>>> RUNNING PRESET: HAPPY PATH E-COMMERCE WORKFLOW <<<");
        String signedId = tokenVerifier.generateSignedId("CUSTOMER_01");

        processRequest(signedId, "POST_LOGIN", true);
        processRequest(signedId, "POST_CART", true);
        processRequest(signedId, "POST_PAY", true);
        processRequest(signedId, "POST_CHECKOUT", true);

        System.out.println("\n[SCENARIO RESULT]: User completed full purchase flow (START -> AUTH -> CART -> PAY -> CONFIRM)!");
    }

    private void runPresetStateSkipAttack() {
        System.out.println("\n>>> RUNNING PRESET: STATE-SKIP ATTACK DETECTION <<<");
        String signedId = tokenVerifier.generateSignedId("ATTACKER_99");

        System.out.println("1. Attacker logs in legitimately:");
        processRequest(signedId, "POST_LOGIN", true);

        System.out.println("\n2. Attacker attempts to skip CART and PAYMENT, jumping straight to CHECKOUT:");
        processRequest(signedId, "POST_CHECKOUT", true);

        System.out.println("\n[SCENARIO RESULT]: State skip detected and blocked! Attacker session terminated.");
    }

    private void runPresetForgedTokenAttack() {
        System.out.println("\n>>> RUNNING PRESET: FORGED HMAC SIGNATURE ATTACK <<<");
        String forgedId = "VICTIM_USER.invalid_tampered_signature_999";

        System.out.println("Attacker sends fabricated session cookie:");
        processRequest(forgedId, "POST_LOGIN", true);

        System.out.println("\n[SCENARIO RESULT]: Forgery caught at Layer 1 by HMAC-SHA256 verifier!");
    }

    private void runPresetBackwardNavigation() {
        System.out.println("\n>>> RUNNING PRESET: BACKWARD NAVIGATION & CART RESET <<<");
        String signedId = tokenVerifier.generateSignedId("SHOPPER_42");

        processRequest(signedId, "POST_LOGIN", true);
        processRequest(signedId, "POST_CART", true);
        processRequest(signedId, "POST_PAY", true);

        System.out.println("\nShopper changes mind and cancels payment (PAY -> CART):");
        processRequest(signedId, "CANCEL_PAY", true);

        System.out.println("\nShopper deletes cart (CART -> AUTH):");
        processRequest(signedId, "DELETE_CART", true);

        System.out.println("\nShopper logs out (AUTH -> START):");
        processRequest(signedId, "POST_LOGOUT", true);

        System.out.println("\n[SCENARIO RESULT]: Backward transitions handled gracefully and tape safely managed!");
    }

    private void runPresetMultiUserIsolation() {
        System.out.println("\n>>> RUNNING PRESET: MULTI-USER SESSION ISOLATION <<<");
        String alice = tokenVerifier.generateSignedId("ALICE");
        String bob = tokenVerifier.generateSignedId("BOB");

        System.out.println("1. Alice logs in and adds items to cart:");
        processRequest(alice, "POST_LOGIN", true);
        processRequest(alice, "POST_CART", true);

        System.out.println("\n2. Bob logs in:");
        processRequest(bob, "POST_LOGIN", true);

        System.out.println("\n3. Alice's state is " + secureTracker.getCurrentState(alice)
                + " | Bob's state is " + secureTracker.getCurrentState(bob));

        System.out.println("\n4. Bob attempts an illegal state skip to CHECKOUT:");
        processRequest(bob, "POST_CHECKOUT", true);

        System.out.println("\n5. Checking states after Bob's attack:");
        System.out.println("   Alice's State : " + secureTracker.getCurrentState(alice) + " (Unaffected!)");
        System.out.println("   Bob's State   : " + secureTracker.getCurrentState(bob) + " (Destroyed!)");
        System.out.println("   Active Sessions: " + secureTracker.getActiveSessionCount());
    }

    private void displayActiveSessions() {
        System.out.println("\n=== [ACTIVE SESSIONS TABLE] ===");
        System.out.println("Total Active Sessions: " + secureTracker.getActiveSessionCount());
        if (secureTracker.getActiveSessionCount() == 0) {
            System.out.println("No active sessions currently tracked.");
            return;
        }

        for (String rawId : baseTracker.getSessions().keySet()) {
            TuringMachine tm = baseTracker.getSessionMachine(rawId);
            String state = (tm != null) ? tm.getCurrentState() : "UNKNOWN";
            int tapeLen = (tm != null) ? tm.getTape().getRawTape().getLength() : 0;
            System.out.println(
                    "  - Session: " + rawId + " | Current State: " + state + " | Encrypted Tape Length: " + tapeLen);
        }
    }

    private void printBanner() {
        System.out.println("========================================================================");
        System.out.println("   NEXT-GEN WAF - WORKLET 3: STATEFUL AUTOMATA & SESSION INSPECTOR      ");
        System.out.println("========================================================================");
        System.out.println("Layers Invoked:");
        System.out.println("  [1] SessionTokenVerifier : HMAC-SHA256 Signature Verification");
        System.out.println("  [2] SessionTracker       : ConcurrentHashMap Multi-User Session Manager");
        System.out.println("  [3] TuringMachine        : Finite Automata & Turing Machine State Engine");
        System.out.println("  [4] SecureRuleBook       : In-Memory SHA-256 Tamper-Proof Transition Rules");
        System.out.println("  [5] SecureTape           : AES-128 Encrypted Doubly Linked List Memory");
        System.out.println("========================================================================");
    }

    private void printMenu() {
        System.out.println("\nMAIN MENU:");
        System.out.println("  [1] Interactive Request Simulator (Enter Session ID + Action)");
        System.out.println("  [2] Run Preset: Happy Path E-Commerce Flow (Login -> Cart -> Pay -> Checkout)");
        System.out.println("  [3] Run Preset: State-Skip Attack (Login -> Direct Checkout)");
        System.out.println("  [4] Run Preset: Forged HMAC Signature Attack");
        System.out.println("  [5] Run Preset: Backward Navigation & Reset (Cancel Pay / Delete Cart / Logout)");
        System.out.println("  [6] Run Preset: Multi-User Session Isolation (Alice vs Bob)");
        System.out.println("  [7] View Active Sessions Summary");
        System.out.println("  [8] View Encrypted Tape Content (Detailed Cell-by-Cell Dump)");
        System.out.println("  [9] Exit");
    }
}
