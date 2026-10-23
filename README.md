# WAF Firewall - Multi-Layer Integrated Web Application Firewall & Reverse Proxy

An enterprise-grade, defense-in-depth Web Application Firewall (WAF) and transparent **Reverse Proxy** designed to protect modern web applications against injection attacks, structural syntax anomalies, and unauthorized business workflow bypasses.

The firewall intercepts all incoming HTTP traffic through an integrated **3-Worklet Pipeline** before determining whether to forward the request to the upstream web application or reject it with an immediate `403 Forbidden` security diagnostic.

---

## Table of Contents
1. [Architectural Overview](#architectural-overview)
2. [End-to-End Request Pipeline](#end-to-end-request-pipeline)
3. [Deep-Dive: Individual Worklets](#deep-dive-individual-worklets)
   - [Worklet 1: Lexical, Regex Automata & Signature Engine (Java)](#worklet-1-lexical-regex-automata--signature-engine-java)
   - [Worklet 2: CYK Structural Parser & Grammar Engine (C++17)](#worklet-2-cyk-structural-parser--grammar-engine-c17)
   - [Worklet 3: Stateful Turing Machine Workflow Tracker (Java)](#worklet-3-stateful-turing-machine-workflow-tracker-java)
4. [Session Tracking & Route Action Resolution](#session-tracking--route-action-resolution)
5. [Valid vs. Invalid Request Scenarios](#valid-vs-invalid-request-scenarios)
6. [Repository Structure](#repository-structure)
7. [Prerequisites & Build Guide](#prerequisites--build-guide)
8. [Running & Verification](#running--verification)

---

## Architectural Overview

Traditional firewalls only check pattern signatures (e.g., regex matching), leaving applications vulnerable to sophisticated attacks like syntactic parser desynchronization or business logic order-of-operation bypasses.

This project implements a multi-tier defense across three distinct formal computational models:

| Tier | Component | Technology | Formal Model | Primary Responsibility |
|---|---|---|---|---|
| **Tier 1** | **Worklet 1** | Java 17 | **Finite Automata (NFA / DFA)** | Lexical normalization, fast regex signature detection (SQLi, XSS, Path Traversal), tokenization, and session extraction. |
| **Tier 2** | **Worklet 2** | C++17 | **Context-Free Grammar (CYK)** | Deep structural AST parsing via Chomsky Normal Form (CNF) grammar, delimiter pairing, and nesting depth validation. |
| **Tier 3** | **Worklet 3** | Java 17 | **Turing Machine (State Automaton)** | Session-aware business workflow enforcement (e.g. forcing strict authentication and cart sequences before checkout). |

```
                       +-------------------------------------------------------------+
                       |              WAF Reverse Proxy (Port 8080)                  |
                       |                                                             |
   Incoming HTTP       |  1. Worklet 1: Lexical & Signature Engine                   |
   Request             |     - Regex DFA Signature Scan (SQLi, XSS, Path Traversal)  |
---------------------> |     - Lexical JSON Tokenizer                                |
 (Browser / Postman)   |     - Session Cookie Extraction (WAF_SID)                   |
                       |                     |                                       |
                       |                     v (Passes JSON Tokens)                  |
                       |  2. Worklet 2: C++ CYK Parser Process                       |
                       |     - Chomsky Normal Form Grammar Parsing                   |
                       |     - Delimiter & Tree Depth Validation                     |
                       |                     |                                       |
                       |                     v (Passes Session + Route Action)       |
                       |  3. Worklet 3: Turing Machine State Engine                  |
                       |     - State Transition Verification                         |
                       |     - Memory Integrity Verification (SHA-256)               |
                       |                     |                                       |
                       +---------------------|---------------------------------------+
                                             |
                               [ All Passed? ]
                                /           \
                           YES /             \ NO (Any Worklet Rejects)
                              v               v
                +---------------------+   +-----------------------------------------+
                | Protected Backend   |   | HTTP 403 Forbidden                      |
                | (e.g., Port 3000)   |   | JSON Error with offending worklet stage |
                +---------------------+   +-----------------------------------------+
```

---

## End-to-End Request Pipeline

When a request enters `WafReverseProxyServer` (default port `8080`):
1. **Route-to-Action Resolution**: The proxy inspects the HTTP method and URI path (e.g. `POST /api/login` -> `POST_LOGIN`, `POST /api/checkout` -> `POST_CHECKOUT`).
2. **Worklet 1 Inspection**:
   - Normalizes the request URI, headers, and body.
   - Evaluates all fields against compiled DFAs loaded from `rules.txt`.
   - If an attack pattern is found (e.g., `' OR '1'='1`), the pipeline halts and returns `403 Forbidden` with diagnostic stage `Worklet 1 (HTTP/Signatures)`.
   - If payload is JSON, tokenizes it into a formal token sequence.
   - Extracts the session identifier from the `WAF_SID` cookie or `X-Session-ID` header.
3. **Worklet 2 Validation**:
   - The token stream is forwarded over standard IPC to the high-performance C++ Worklet 2 binary.
   - The CYK parser validates that the token structure strictly adheres to the CFG JSON grammar rules in `cfg_rules.json`.
   - If unbalanced brackets, illegal token orders, or nesting violations exist, returns `403 Forbidden` with diagnostic stage `Worklet 2 (CYK/Structure)`.
4. **Worklet 3 State Enforcement**:
   - Checks the session's current state on the Turing Machine tape.
   - Validates whether the incoming action is a legal transition from the current state (e.g., `START` -> `POST_LOGIN` -> `AUTH` is valid; `START` -> `POST_CHECKOUT` is denied).
   - If valid, commits the state transition; otherwise, returns `403 Forbidden` with diagnostic stage `Worklet 3 (Turing Machine State)`.
5. **Upstream Forwarding**:
   - Transparently proxies the verified request to the target application (`http://localhost:3000`), relays headers, and streams the target response back to the client.

---

## Deep-Dive: Individual Worklets

### Worklet 1: Lexical, Regex Automata & Signature Engine (Java)
- **Path**: `src/main/java/com/wafgateway/worklet1/`
- **Formal Theory**: Regular Expressions, Non-Deterministic Finite Automata (NFA), Deterministic Finite Automata (DFA).
- **Core Modules**:
  - `ThompsonBuilder`: Converts parsed regex ASTs into NFAs with $\epsilon$-transitions.
  - `SubsetConstruction`: Transforms NFAs into equivalent DFAs for $O(N)$ string evaluation independent of pattern complexity.
  - `HopcroftMinimizer`: Minimizes DFA states to ensure minimal memory footprint and maximum throughput.
  - `SignatureScanner`: Evaluates input against rules defined in `rules.txt` (categorized by SQLi, XSS, LFI/RFI, and Command Injection).
  - `Normalizer`: Handles URL encoding/decoding, whitespace collapsing, and lowercase mapping.
  - `JsonTokenizer`: A robust lexical scanner producing strongly typed tokens (`LEFT_BRACE`, `STRING`, `COLON`, `NUMBER`, `BOOLEAN`, `COMMA`, `RIGHT_BRACE`).

### Worklet 2: CYK Structural Parser & Grammar Engine (C++17)
- **Path**: `worklet2/` and `src/main/java/com/wafgateway/worklet2/`
- **Formal Theory**: Context-Free Grammars (CFG), Chomsky Normal Form (CNF), Cocke-Younger-Kasami (CYK) $O(n^3)$ Dynamic Programming Algorithm.
- **Core Modules**:
  - `worklet2/src/grammar/`: Loads CNF grammar definitions from `cfg_rules.json` and builds $O(1)$ hashed lookup rule indices.
  - `worklet2/src/cyk/`: Implements the 2D triangular dynamic programming table (`CYKTable.cpp` & `Cell.cpp`) for parsing.
  - `worklet2/src/tree/`: `TreeBuilder` reconstructs the parse tree; `TreeChecker` enforces semantic limits such as maximum nesting depth and paired bracket matching.
  - `Worklet2ProcessClient.java`: Manages the C++ process lifecycle, marshaling Java tokens to stdin as JSON and parsing stdout validation responses.

### Worklet 3: Stateful Turing Machine Workflow Tracker (Java)
- **Path**: `src/main/java/com/wafgateway/worklet3/`
- **Formal Theory**: Deterministic Turing Machines (DTM) and Finite State Automata.
- **Core Modules**:
  - `SecureTape`: Represents the persistent session tape storing state markers and execution history.
  - `SecureRuleBook`: The transition function $\delta(Q, \Sigma) \to Q$. Uses immutable transition maps and runtime **SHA-256 self-hashing** to detect RAM injection/tampering.
  - `SessionTracker`: Tracks active sessions in memory, providing concurrency-safe `checkValid()` and `commit()` operations.
  - **State Transition Graph**:
    - `START` $\xrightarrow{\text{POST /api/login}}$ `AUTH`
    - `AUTH` $\xrightarrow{\text{POST /api/cart}}$ `CART`
    - `AUTH` or `CART` $\xrightarrow{\text{GET /api/cart}}$ (No state change)
    - `CART` $\xrightarrow{\text{PUT /api/cart}}$ (No state change)
    - `CART` $\xrightarrow{\text{POST /api/pay}}$ `PAY`
    - `PAY` $\xrightarrow{\text{POST /api/checkout}}$ `CONFIRM`
    - Reset/Backtrack routes: `DELETE_CART` $\to$ `AUTH`, `CANCEL_PAY` $\to$ `CART`.
    - **Default-Deny**: Any unmapped transition is rejected instantly.

---

## Session Tracking & Route Action Resolution

### Dynamic Route Extraction (No forced body payload)
Clients do not need to embed synthetic action names into request bodies. The reverse proxy automatically extracts actions from natural HTTP requests:

| HTTP Request | Mapped WAF Action | Description |
|---|---|---|
| `POST /api/login` | `POST_LOGIN` | User authentication |
| `POST /api/cart` | `POST_CART` | Add item to cart |
| `GET /api/cart` | `GET_CART` | Retrieve current cart items |
| `PUT /api/cart` | `PUT_CART` | Modify cart quantities |
| `DELETE /api/cart` | `DELETE_CART` | Clear cart / reset to authenticated |
| `POST /api/pay` | `POST_PAY` | Initiate payment |
| `POST /api/checkout` | `POST_CHECKOUT` | Final order confirmation |
| `GET /` or `GET /api/home` | `GET_HOME` | Safe home browsing |
| `GET /health` or `GET /favicon.ico` | `GET_STATIC` | Public bypassed static routes |

### Session Identification
The proxy identifies distinct users via:
1. **HTTP Cookie**: `WAF_SID=<session_identifier>` (configured in `worklet1.properties`).
2. **HTTP Header**: `X-Session-ID: <session_identifier>` (fallback for API clients).
3. **Default Session**: If unauthenticated or absent on public routes, defaults safely to `session_default`.

---

## Valid vs. Invalid Request Scenarios

### 1. Normal E-Commerce Flow (All 3 Worklets Pass)

#### Step 1: Login
```http
POST http://localhost:8080/api/login
Content-Type: application/json
Cookie: WAF_SID=user_alice_123

{
  "username": "alice",
  "password": "SecurePass123!"
}
```
*Verdict*: `HTTP 200 OK` (State advances: `START` -> `AUTH`).

#### Step 2: Add to Cart
```http
POST http://localhost:8080/api/cart
Content-Type: application/json
Cookie: WAF_SID=user_alice_123

{
  "itemId": "item-9901",
  "quantity": 2
}
```
*Verdict*: `HTTP 200 OK` (State advances: `AUTH` -> `CART`).

#### Step 3: Payment
```http
POST http://localhost:8080/api/pay
Content-Type: application/json
Cookie: WAF_SID=user_alice_123

{
  "method": "card",
  "amount": 59.99
}
```
*Verdict*: `HTTP 200 OK` (State advances: `CART` -> `PAY`).

#### Step 4: Checkout
```http
POST http://localhost:8080/api/checkout
Content-Type: application/json
Cookie: WAF_SID=user_alice_123

{
  "orderRef": "REF-2026-001"
}
```
*Verdict*: `HTTP 200 OK` (State advances: `PAY` -> `CONFIRM`).

---

### 2. Blocked by Worklet 1: SQL Injection
```http
POST http://localhost:8080/api/login
Content-Type: application/json
Cookie: WAF_SID=user_attacker

{
  "username": "admin' OR '1'='1",
  "password": "pass"
}
```
*Response*:
```json
{
  "error": "Forbidden by WAF",
  "stage": "Worklet 1 (HTTP/Signatures)",
  "action": "POST_LOGIN",
  "details": "Blocked by Worklet 1: SIGNATURE_MATCH - SQL Injection signature detected"
}
```

---

### 3. Blocked by Worklet 2: Structural / Syntactic Malformation
```http
POST http://localhost:8080/api/cart
Content-Type: application/json
Cookie: WAF_SID=user_alice_123

{
  "itemId": "item-9901",
  "quantity": 2,
}}
```
*Response*:
```json
{
  "error": "Forbidden by WAF",
  "stage": "Worklet 2 (CYK/Structure)",
  "action": "POST_CART",
  "details": "Blocked by Worklet 2: SYNTAX_ERROR - Unmatched delimiters or CFG parse failure"
}
```

---

### 4. Blocked by Worklet 3: Out-of-Sequence Workflow Bypass
Attempting to jump straight to checkout without logging in or adding items to cart:
```http
POST http://localhost:8080/api/checkout
Content-Type: application/json
Cookie: WAF_SID=user_bob_456

{
  "orderRef": "REF-SKIP-AUTH"
}
```
*Response*:
```json
{
  "error": "Forbidden by WAF",
  "stage": "Worklet 3 (Turing Machine State)",
  "action": "POST_CHECKOUT",
  "details": "Blocked by Worklet 3: Invalid transition for action 'POST_CHECKOUT' in state START"
}
```

---

## Repository Structure

```
WAF-Firewall/
|-- pom.xml                                  # Maven project configuration (Java 17)
|-- README.md                                # Master project documentation
|-- run_all.ps1                              # Automated end-to-end suite runner
|-- run_waf_proxy.bat                        # Reverse proxy start script
|-- cfg_rules.json                           # Chomsky Normal Form CFG rules
|
|-- src/main/java/com/wafgateway/
|   |-- common/                              # Common models (HttpRequest, Token, TokenType)
|   |-- pipeline/
|   |   `-- IntegratedPipeline.java          # Orchestrator chaining Worklet 1 -> 2 -> 3
|   |-- proxy/
|   |   |-- WafReverseProxyServer.java       # Reverse proxy HTTP server implementation
|   |   `-- MockWebsiteServer.java           # Embedded mock backend for testing
|   |-- worklet1/                            # Tier 1: Lexical, Regex DFA & Signatures
|   |   |-- automata/                        # Thompson NFA, Subset DFA, Hopcroft Minimizer
|   |   |-- regex/                           # Regex AST parser & ByteSet
|   |   |-- request/                         # Field extractors & Normalizer
|   |   |-- signature/                       # Rule loader, Rule matcher, Severity
|   |   |-- tokenizer/                       # RFC-compliant JSON lexical scanner
|   |   `-- session/                         # Session ID validator
|   |-- worklet2/                            # Tier 2: Java-to-C++ Process Client Bridge
|   |   |-- TokenBridge.java
|   |   |-- Worklet2ProcessClient.java
|   |   `-- Worklet2Result.java
|   `-- worklet3/                            # Tier 3: Turing Machine Session Tracker
|       |-- engine/                          # SecureRuleBook (SHA-256 protected), TuringMachine
|       |-- session/                         # SessionTracker, SessionTokenVerifier
|       `-- tape/                            # SecureTape & TapeNode
|
|-- src/main/resources/
|   |-- rules.txt                            # Signature definitions (SQLi, XSS, Traversal)
|   `-- worklet1.properties                  # Worklet 1 runtime limits & configuration
|
`-- worklet2/                                # C++17 CYK Grammar & Tree Validation Engine
    |-- CMakeLists.txt                       # CMake build config
    |-- config/cfg_rules.json                # Grammar rules copy for C++ runtime
    |-- include/worklet2/                    # Header files
    |-- src/
    |   |-- main.cpp                         # Stdin/stdout JSON IPC runner
    |   |-- cyk/                             # CYK algorithm & DP table implementation
    |   |-- grammar/                         # CNF rules & indexing
    |   |-- tree/                            # AST Builder, Tree Checker & Action Extractor
    |   `-- pipeline/                        # Structural validator & gateway
    `-- tests/                               # Benchmarks & integration test suites
```

---

## Prerequisites & Build Guide

### Prerequisites
- **Java**: JDK 17 or higher
- **Build Tool**: Apache Maven 3.8+
- **C++ Compiler**: GCC / Clang / MSVC supporting C++17
- **CMake**: Version 3.14+ (for building Worklet 2 C++ engine)

### Step 1: Build C++ Worklet 2
```powershell
cd worklet2
mkdir build
cd build
cmake ..
cmake --build . --config Release
cd ../..
```
*Note: A precompiled `worklet2/worklet2_demo.exe` is already provided in the repository for Windows.*

### Step 2: Build Java WAF Gateway
```powershell
mvn clean compile
```

---

## Running & Verification

### Option A: Run Full Automated Verification Suite
Run the unified test runner to execute Worklet 2 benchmarks, unit tests, and the end-to-end integrated 3-worklet pipeline:
```powershell
.\run_all.ps1
```

### Option B: Start WAF Reverse Proxy Server
Start the transparent reverse proxy listening on port `8080`, forwarding authorized traffic to an upstream application running on port `3000`:
```cmd
run_waf_proxy.bat 8080 http://localhost:3000
```
Or directly via Java:
```powershell
java -cp "target/classes" com.wafgateway.proxy.WafReverseProxyServer --port 8080 --target http://localhost:3000
```

You can now point your frontend, mobile application, or Postman requests to `http://localhost:8080` to have all traffic inspected in real time by the WAF.
