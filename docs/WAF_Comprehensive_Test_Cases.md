# WAF-Firewall: Comprehensive Security & Functional Test Cases

This document contains the complete test suite for the Web Application Firewall (WAF), specifically covering **Worklet 3's cryptographic and stateful security features** (`SecureSessionTracker`, `SecureTape`, `SecureRuleBook`, and `TuringMachine`), along with end-to-end **Postman test cases**.

---

## Table of Contents
1. [Postman Environment Setup](#postman-environment-setup)
2. [Category 1: Stateful E-Commerce Flow Tests (Postman)](#category-1-stateful-e-commerce-flow-tests-postman)
3. [Category 2: First-Action-Login Security Tests (Postman)](#category-2-first-action-login-security-tests-postman)
4. [Category 3: State-Skip & Workflow Manipulation Attacks (Postman)](#category-3-state-skip--workflow-manipulation-attacks-postman)
5. [Category 4: Backward Navigation & Cancellation Tests (Postman)](#category-4-backward-navigation--cancellation-tests-postman)
6. [Category 5: Session Termination & Replay Attacks (Postman)](#category-5-session-termination--replay-attacks-postman)
7. [Category 6: SecureSessionTracker & HMAC-SHA256 Cryptographic Tests](#category-6-securesessiontracker--hmac-sha256-cryptographic-tests)
8. [Category 7: SecureTape & AES-128 In-Memory Encryption Tests](#category-7-securetape--aes-128-in-memory-encryption-tests)
9. [Category 8: SecureRuleBook SHA-256 Anti-Tamper Tests](#category-8-securerulebook-sha-256-anti-tamper-tests)
10. [Category 9: Multi-Tenant Session Isolation & Quota Tests](#category-9-multi-tenant-session-isolation--quota-tests)
11. [Master Test Execution Matrix](#master-test-execution-matrix)

---

## Postman Environment Setup

* **WAF Proxy URL**: `http://localhost:8080`
* **Target Website (Backend)**: `http://localhost:3000` (run `MockWebsiteServer 3000`)
* **Default Headers**:
  * `Content-Type: application/json`
  * `Cookie: session_id=<32_hex_chars>` (e.g. `11111111111111111111111111111111`)

> [!IMPORTANT]
> Always quote numeric values in JSON (e.g., `"amount": "500"` instead of `"amount": 500`) to conform to Worklet 2's CYK Context-Free Grammar parser rules.

---

## Category 1: Stateful E-Commerce Flow Tests (Postman)

Validates the full legal progression of the Turing Machine:  
$$\text{START} \xrightarrow{\text{POST\_LOGIN}} \text{AUTH} \xrightarrow{\text{POST\_CART}} \text{CART} \xrightarrow{\text{POST\_PAY}} \text{PAY} \xrightarrow{\text{POST\_CHECKOUT}} \text{CONFIRM}$$

### TC-F01 (Positive): User Login
* **Method & URL**: `POST http://localhost:8080/api/login`
* **Headers**: `Content-Type: application/json`, `Cookie: session_id=10101010101010101010101010101010`
* **Body** (`raw JSON`):
  ```json
  {"action": "LOGIN", "username": "alice"}
  ```
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**:
  - `SessionTracker` creates new `TuringMachine` initialized at `START`.
  - `checkValid("POST_LOGIN")` resolves `(START, POST_LOGIN) -> AUTH:RIGHT`.
  - `commit("POST_LOGIN")` moves tape right, AES-128 encrypts `"AUTH"`, and writes to `SecureTape`.

### TC-F02 (Positive): Add Item to Cart
* **Method & URL**: `POST http://localhost:8080/api/cart`
* **Headers**: `Content-Type: application/json`, `Cookie: session_id=10101010101010101010101010101010`
* **Body** (`raw JSON`):
  ```json
  {"action": "CART", "item": "laptop"}
  ```
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**: `(AUTH, POST_CART) -> CART:RIGHT`. State transitions to `CART`.

### TC-F03 (Positive): Proceed to Payment
* **Method & URL**: `POST http://localhost:8080/api/pay`
* **Headers**: `Content-Type: application/json`, `Cookie: session_id=10101010101010101010101010101010`
* **Body** (`raw JSON`):
  ```json
  {"action": "PAY", "method": "upi"}
  ```
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**: `(CART, POST_PAY) -> PAY:RIGHT`. State transitions to `PAY`.

### TC-F04 (Positive): Final Checkout Confirmation
* **Method & URL**: `POST http://localhost:8080/api/checkout`
* **Headers**: `Content-Type: application/json`, `Cookie: session_id=10101010101010101010101010101010`
* **Body** (`raw JSON`):
  ```json
  {"action": "CHECKOUT", "orderId": "ORD-999"}
  ```
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**: `(PAY, POST_CHECKOUT) -> CONFIRM:RIGHT`. State transitions to `CONFIRM`.

---

## Category 2: First-Action-Login Security Tests (Postman)

Enforces that unauthenticated sessions cannot execute any business transaction without logging in first.

### TC-L01 (Positive): First Request Is Login
* **Method & URL**: `POST http://localhost:8080/api/login`
* **Cookie**: `session_id=20202020202020202020202020202020`
* **Body**: `{"action": "LOGIN"}`
* **Expected Result**: **`200 OK` (ALLOWED)**

### TC-L02 (Negative): First Request Is Add to Cart (Unauthenticated)
* **Method & URL**: `POST http://localhost:8080/api/cart`
* **Cookie**: `session_id=30303030303030303030303030303030`
* **Body**: `{"action": "CART", "item": "book"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Details**: `Blocked by Worklet 3: Invalid transition for action 'POST_CART' in state NO_SESSION`
* **Under the Hood**: First-action rule in `SessionTracker` requires `POST_LOGIN`. All other actions on uninitialized sessions are dropped.

### TC-L03 (Negative): First Request Is Direct Checkout
* **Method & URL**: `POST http://localhost:8080/api/checkout`
* **Cookie**: `session_id=40404040404040404040404040404040`
* **Body**: `{"action": "CHECKOUT"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Details**: `Blocked by Worklet 3: Invalid transition for action 'POST_CHECKOUT' in state NO_SESSION`

---

## Category 3: State-Skip & Workflow Manipulation Attacks (Postman)

Detects attackers attempting to bypass intermediate security or payment checkpoints.

### TC-S01 (Negative): Skip Cart (AUTH $\rightarrow$ PAY)
* *Pre-condition*: Login first with `session_id=50505050505050505050505050505050` (state is `AUTH`).
* **Method & URL**: `POST http://localhost:8080/api/pay`
* **Cookie**: `session_id=50505050505050505050505050505050`
* **Body**: `{"action": "PAY"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Details**: `Blocked by Worklet 3: Invalid transition for action 'POST_PAY' in state AUTH`
* **Under the Hood**: Rulebook has no transition for `(AUTH, POST_PAY)`.

### TC-S02 (Negative): Skip Payment (CART $\rightarrow$ CHECKOUT)
* *Pre-condition*: Session `5050...` logs in, then adds to cart (state is `CART`).
* **Method & URL**: `POST http://localhost:8080/api/checkout`
* **Cookie**: `session_id=50505050505050505050505050505050`
* **Body**: `{"action": "CHECKOUT"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Details**: `Blocked by Worklet 3: Invalid transition for action 'POST_CHECKOUT' in state CART`
* **Under the Hood**: You cannot checkout without payment confirmation.

### TC-S03 (Negative): Replay Login Attack (POST_LOGIN from AUTH)
* *Pre-condition*: Session `5050...` is already logged in (state is `AUTH`).
* **Method & URL**: `POST http://localhost:8080/api/login`
* **Cookie**: `session_id=50505050505050505050505050505050`
* **Body**: `{"action": "LOGIN"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Under the Hood**: Prevents session fixation / credential stuffing over active sessions.

### TC-S04 (Negative): Double Checkout Attack (Replay)
* *Pre-condition*: Session `5050...` completes checkout (state is `CONFIRM`).
* **Method & URL**: `POST http://localhost:8080/api/checkout`
* **Cookie**: `session_id=50505050505050505050505050505050`
* **Body**: `{"action": "CHECKOUT"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Under the Hood**: Prevents double order placement / replay fraud.

---

## Category 4: Backward Navigation & Cancellation Tests (Postman)

Validates legal step-backs and enforces that irreversible states cannot be cancelled.

### TC-B01 (Positive): Cancel Payment (PAY $\rightarrow$ CART)
* *Pre-condition*: Session is at state `PAY`.
* **Method & URL**: `DELETE http://localhost:8080/api/pay`
* **Headers**: `Cookie: session_id=<active_session>`
* **Body**: None
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**: `(PAY, CANCEL_PAY) -> CART:LEFT`. Tape head writes blank and moves left; state safely returns to `CART`.

### TC-B02 (Positive): Clear Cart (CART $\rightarrow$ AUTH)
* *Pre-condition*: Session is at state `CART`.
* **Method & URL**: `DELETE http://localhost:8080/api/cart`
* **Headers**: `Cookie: session_id=<active_session>`
* **Body**: None
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**: `(CART, DELETE_CART) -> AUTH:LEFT`. State returns to `AUTH`.

### TC-B03 (Negative): Cancel Payment After Confirmation (CONFIRM $\rightarrow$ PAY)
* *Pre-condition*: Session is at state `CONFIRM`.
* **Method & URL**: `DELETE http://localhost:8080/api/pay`
* **Headers**: `Cookie: session_id=<active_session>`
* **Body**: None
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Under the Hood**: Orders already confirmed cannot be rolled back via payment cancellation.

### TC-B04 (Negative): Delete Cart During Payment Phase (PAY $\rightarrow$ DELETE_CART)
* *Pre-condition*: Session is at state `PAY`.
* **Method & URL**: `DELETE http://localhost:8080/api/cart`
* **Headers**: `Cookie: session_id=<active_session>`
* **Body**: None
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Under the Hood**: Cart is locked while payment is processing.

---

## Category 5: Session Termination & Replay Attacks (Postman)

Validates that `POST_LOGOUT` immediately revokes the session and clears all memory.

### TC-T01 (Positive): Legitimate Session Logout
* *Pre-condition*: Session `60606060606060606060606060606060` is logged in.
* **Method & URL**: `POST http://localhost:8080/api/logout`
* **Cookie**: `session_id=60606060606060606060606060606060`
* **Body**: `{"action": "LOGOUT"}`
* **Expected Result**: **`200 OK` (ALLOWED)**
* **Under the Hood**: `commit()` triggers `clearSession()`. The Turing Machine and its encrypted tape are removed from the `ConcurrentHashMap`.

### TC-T02 (Negative): Re-using Logged-Out Cookie (Session Revocation)
* *Pre-condition*: Execute TC-T01 to log out session `6060...`.
* **Method & URL**: `POST http://localhost:8080/api/cart`
* **Cookie**: `session_id=60606060606060606060606060606060`
* **Body**: `{"action": "CART", "item": "book"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**
* **Details**: `Blocked by Worklet 3: Invalid transition for action 'POST_CART' in state NO_SESSION`
* **Under the Hood**: Verified that revoked sessions cannot be resurrected.

### TC-T03 (Negative): Duplicate Logout on Already Logged-Out Session
* *Pre-condition*: Execute TC-T01 first.
* **Method & URL**: `POST http://localhost:8080/api/logout`
* **Cookie**: `session_id=60606060606060606060606060606060`
* **Body**: `{"action": "LOGOUT"}`
* **Expected Result**: **`403 Forbidden` (BLOCKED)**

---

## Category 6: SecureSessionTracker & HMAC-SHA256 Cryptographic Tests

These test cases verify the integrity and cryptographic resistance of `SecureSessionTracker` and `SessionTokenVerifier`.

### TC-SEC01 (Positive): Valid HMAC-SHA256 Token Accepted
* **Component**: `SessionTokenVerifier` & `SecureSessionTracker`
* **Input**: Validly signed token `SESSION_101.<valid_base64_hmac>`
* **Action**: `POST_LOGIN`
* **Expected Result**: Signature verified $\rightarrow$ returns `true`.
* **Test**: `SessionComprehensiveTest.testValidSignedSessionId()`

### TC-SEC02 (Negative): Forged HMAC Signature Attack
* **Component**: `SessionTokenVerifier`
* **Attack Scenario**: Attacker modifies the signature suffix (e.g. `SESSION_101.ABC...XX`)
* **Expected Result**: Signature verification fails $\rightarrow$ returns `false`.
* **Under the Hood**: `verifySignature()` recomputes `HMAC-SHA256(rawId, secretKey)` and fails constant-time comparison.
* **Test**: `SessionComprehensiveTest.testForgedSessionId()`

### TC-SEC03 (Negative): Missing Signature (Unsigned Token)
* **Component**: `SessionTokenVerifier`
* **Input**: `"SESSION_101"` (no period or signature)
* **Expected Result**: Rejection $\rightarrow$ returns `false`.
* **Test**: `SessionComprehensiveTest.testSessionIdWithoutSignature()`

### TC-SEC04 (Negative): Timing Attack Defense
* **Component**: `SessionTokenVerifier`
* **Implementation Verification**:
  ```java
  MessageDigest.isEqual(actualBytes, expectedBytes);
  ```
* **Security Property**: Verification runs in constant time $O(n)$, preventing side-channel byte-by-byte timing attacks.

### TC-SEC05 (Negative): Immediate Session Destruction on Anomaly
* **Component**: `SessionTracker`
* **Attack Scenario**: Attacker holding an active session attempts an illegal state skip.
* **Expected Result**: Not only is the request blocked (`false`), but `clearSession(rawId)` is executed immediately. The session is evicted from memory.
* **Test**: `SessionComprehensiveTest.testStateSkipAttackBlocked()`

---

## Category 7: SecureTape & AES-128 In-Memory Encryption Tests

Verifies that tape storage is protected against RAM-scraping attacks and in-memory heap manipulation.

### TC-TAPE01 (Positive): Zero Plaintext in RAM (Confidentiality)
* **Component**: `SecureTape` & `Tape`
* **Scenario**: Write states (`"AUTH"`, `"PAY"`) to `SecureTape`.
* **Inspection**: Inspect underlying `rawTape.read()`.
* **Expected Result**:
  - `rawTape.read()` contains **only AES ciphertext** (e.g., `"3q2+7wbK8G...=="`).
  - Plaintext `"AUTH"` or `"PAY"` is **never stored** anywhere in the heap nodes.
* **Test**: `TapeTest.testRawMemoryContainsOnlyGibberish()`

### TC-TAPE02 (Positive): Transparent Decryption for Authorized Engine
* **Component**: `SecureTape`
* **Scenario**: Call `secureTape.read()`.
* **Expected Result**: AES cipher decrypts ciphertext back into the original state string (`"AUTH"`).
* **Test**: `TapeTest.testSecureTapeEncryptionTransparency()`

### TC-TAPE03 (Negative): Memory Injection / RAM Tampering Detection
* **Component**: `SecureTape`
* **Attack Scenario**: An attacker with root/JVM access modifies raw memory:
  ```java
  secureTape.write("CART");
  // Attacker overwrites node data directly in heap:
  secureTape.rawTape.write("HACKED_MEMORY_STRING");
  ```
* **Expected Result**:
  - When `secureTape.read()` is called, AES decryption fails padding/block checks.
  - The catch block returns **`"TAMPERED"`** instead of crashing!
  - Fail-secure: `TuringMachine` rejects any transition where the tape reports `"TAMPERED"`.
* **Test**: `TapeTest.testTamperDetectionSingleCell()`

### TC-TAPE04 (Positive): Infinite Dynamic Tape Expansion
* **Component**: `Tape` & `TapeNode`
* **Scenario**: Move tape head 100 times to the right.
* **Expected Result**: Doubly linked list dynamically instantiates blank nodes (`"B"`) without out-of-bounds errors or memory leaks.
* **Test**: `TapeTest.testExtremeTapeGrowth()`

---

## Category 8: SecureRuleBook SHA-256 Anti-Tamper Tests

Protects the state transition matrix against malicious in-memory rule injection.

### TC-RULE01 (Positive): Startup Hash Generation
* **Component**: `SecureRuleBook`
* **Behavior**: At instantiation, rules are compiled and an SHA-256 digest is generated over all transition pairs.

### TC-RULE02 (Positive): Rule Immutability Enforcement
* **Component**: `SecureRuleBook`
* **Verification**:
  ```java
  this.rules = Collections.unmodifiableMap(initialRules);
  ```
* **Expected Result**: Any attempt to call `rules.put(...)` throws `UnsupportedOperationException`.

### TC-RULE03 (Negative): In-Memory Tamper Detection
* **Component**: `SecureRuleBook`
* **Verification**: Before every transition lookup in `getNextState()`:
  ```java
  if (!verifyIntegrity()) {
      return "TAMPERED";
  }
  ```
* **Security Property**: If hash verification fails, returns `"TAMPERED"`, halting TM execution.

---

## Category 9: Multi-Tenant Session Isolation & Quota Tests

### TC-ISO01 (Positive): Independent State Progression
* **Scenario**:
  - User A (session `AAA...`) logs in and moves to `CART`.
  - User B (session `BBB...`) logs in and remains in `AUTH`.
* **Expected Result**:
  - `getCurrentState("AAA...")` $\rightarrow$ `CART`
  - `getCurrentState("BBB...")` $\rightarrow$ `AUTH`
* **Test**: `SessionComprehensiveTest.testTwoUsersIndependent()`

### TC-ISO02 (Positive): Attacker Anomaly Does Not Affect Legitimate Users
* **Scenario**:
  - User A executes an invalid state skip (session destroyed).
  - User B continues legitimate shopping.
* **Expected Result**: User B's session remains fully operational and unaffected.
* **Test**: `SessionComprehensiveTest.testInvalidActionDoesNotAffectOtherUser()`

### TC-ISO03 (Negative): Memory Exhaustion / Session Quota Defense (DoS)
* **Component**: `SessionTracker`
* **Scenario**: Generate sessions exceeding `MAX_SESSIONS` limit (10,000 sessions).
* **Expected Result**: New sessions are rejected until existing sessions expire or log out, preventing JVM `OutOfMemoryError`.

---

## Master Test Execution Matrix

| Test ID | Category | Target Component | Attack / Behavior | Execution Tool | Expected Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-F01** | Functional | Full Pipeline | Valid Login (`START -> AUTH`) | Postman | **200 OK** |
| **TC-F02** | Functional | Full Pipeline | Add to Cart (`AUTH -> CART`) | Postman | **200 OK** |
| **TC-F03** | Functional | Full Pipeline | Payment (`CART -> PAY`) | Postman | **200 OK** |
| **TC-F04** | Functional | Full Pipeline | Checkout (`PAY -> CONFIRM`) | Postman | **200 OK** |
| **TC-L01** | Security | `SessionTracker` | Valid First-Action Login | Postman | **200 OK** |
| **TC-L02** | Security | `SessionTracker` | Unauthenticated Cart Attempt | Postman | **403 Forbidden** |
| **TC-L03** | Security | `SessionTracker` | Unauthenticated Checkout | Postman | **403 Forbidden** |
| **TC-S01** | Security | `TuringMachine` | Skip Cart (`AUTH -> PAY`) | Postman | **403 Forbidden** |
| **TC-S02** | Security | `TuringMachine` | Skip Pay (`CART -> CHECKOUT`) | Postman | **403 Forbidden** |
| **TC-S03** | Security | `TuringMachine` | Replay Login from `AUTH` | Postman | **403 Forbidden** |
| **TC-S04** | Security | `TuringMachine` | Double Checkout Replay | Postman | **403 Forbidden** |
| **TC-B01** | Functional | `TuringMachine` | Cancel Payment (`PAY -> CART`) | Postman | **200 OK** |
| **TC-B02** | Functional | `TuringMachine` | Empty Cart (`CART -> AUTH`) | Postman | **200 OK** |
| **TC-B03** | Security | `TuringMachine` | Cancel Payment after Confirmation | Postman | **403 Forbidden** |
| **TC-B04** | Security | `TuringMachine` | Delete Cart during Payment | Postman | **403 Forbidden** |
| **TC-T01** | Functional | `SessionTracker` | User Logout | Postman | **200 OK** |
| **TC-T02** | Security | `SessionTracker` | Replay of Logged-Out Cookie | Postman | **403 Forbidden** |
| **TC-SEC01**| Cryptographic| `SessionTokenVerifier`| Valid HMAC-SHA256 Token | JUnit / Pipeline | **Verified** |
| **TC-SEC02**| Cryptographic| `SessionTokenVerifier`| Forged HMAC Signature | JUnit / Pipeline | **Blocked (False)** |
| **TC-SEC03**| Cryptographic| `SessionTokenVerifier`| Unsigned Cookie String | JUnit / Pipeline | **Blocked (False)** |
| **TC-SEC04**| Cryptographic| `SessionTokenVerifier`| Constant-Time `isEqual` | Code Review | **Protected** |
| **TC-SEC05**| Security | `SessionTracker` | Anomaly Session Auto-Purge | JUnit / Pipeline | **Session Destroyed** |
| **TC-TAPE01**| Storage Sec | `SecureTape` | RAM Zero Plaintext (AES Cipher) | JUnit (`TapeTest`) | **Passed (Encrypted)**|
| **TC-TAPE02**| Storage Sec | `SecureTape` | Transparent AES Decryption | JUnit (`TapeTest`) | **Passed** |
| **TC-TAPE03**| Storage Sec | `SecureTape` | Memory Modification Tamper Check| JUnit (`TapeTest`) | **Returns TAMPERED** |
| **TC-RULE01**| Integrity | `SecureRuleBook` | SHA-256 Hash Generation | JUnit (`EngineTest`)| **Passed** |
| **TC-RULE02**| Integrity | `SecureRuleBook` | Immutable Rule Map | JUnit (`EngineTest`)| **Immutable** |
| **TC-RULE03**| Integrity | `SecureRuleBook` | Rule Modification Tamper Check | JUnit (`EngineTest`)| **Returns TAMPERED** |
| **TC-ISO01**| Multi-User | `SessionTracker` | User A vs User B Isolation | JUnit / Postman | **Independent** |
| **TC-ISO02**| Multi-User | `SessionTracker` | Cross-Session Attack Isolation | JUnit | **Isolated** |
| **TC-ISO03**| DoS Defense | `SessionTracker` | `MAX_SESSIONS` Quota Limit | JUnit | **Cap Enforced** |

