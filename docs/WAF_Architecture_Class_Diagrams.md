# WAF-Firewall: Comprehensive Architecture Class Diagrams

This document contains modular Mermaid class diagrams for each component of the Web Application Firewall (WAF) and shows how `IntegratedPipeline` coordinates them.
This document details the object-oriented structure of each worklet and explicitly maps the exact method invocations and data flows from `IntegratedPipeline.java`.

---

## 1. Worklet 1: Lexical Analysis, Regex DFA & Session Validation

## 1. Master Pipeline Call Flow: `IntegratedPipeline.java` Calling Each Worklet

Worklet 1 performs line-speed inspection of HTTP requests: signature scanning (SQLi, XSS, Path Traversal) via minimized DFAs, JSON tokenization, and cookie validation.
This diagram explicitly illustrates **how `IntegratedPipeline.process(HttpRequest)` invokes the entry-point methods of each worklet**, what inputs it passes, and what results it receives back.

```mermaid
classDiagram
    direction TB

    class IntegratedPipeline {
        -Worklet1 worklet1
        -Worklet2ProcessClient worklet2
        -SecureSessionTracker w3SessionTracker
        +process(HttpRequest request) PipelineDecision
        -resolveAction(HttpRequest request) String
    }

    class HttpRequest {
        -String method
        -String path
        -Map~String, String~ headers
        -Map~String, String~ queryParams
        -String body
        +method() String
        +path() String
        +headers() Map
        +queryParams() Map
        +body() String
    }

    class PipelineDecision {
        +boolean allowed
        +String stage
        +String sessionId
        +String action
        +String details
    }

    %% Step 1: Action Resolution
    class ActionExtractor {
        +extractAction(String method, String path)$ String
    }

    %% Step 2: Worklet 1
    class Worklet1 {
        +inspect(HttpRequest request) Worklet1Result
    }
    class Worklet1Result {
        -boolean blocked
        -String reason
        -String detail
        -String sessionId
        -List~JsonToken~ tokens
        +blocked() boolean
        +reason() String
        +detail() String
        +sessionId() String
        +tokens() List
        +tokens() List~JsonToken~
    }

    %% Step 3: Worklet 2
    class Worklet2ProcessClient {
        +validate(String signedSessionId, String method, List~JsonToken~ tokens) Worklet2Result
    }
    class Worklet2Result {
        +success() boolean
        +reason() String
        +rejectReason() String
    }

    %% Step 4: Worklet 3
    class SecureSessionTracker {
        +generateSignedSessionId(String rawId) String
        +checkValid(String signedSessionId, String action) boolean
        +commit(String signedSessionId, String action) void
        +getCurrentState(String signedSessionId) String
    }

    %% Calls / Associations
    IntegratedPipeline ..> HttpRequest : receives request
    IntegratedPipeline --> PipelineDecision : returns final verdict

    IntegratedPipeline ..> ActionExtractor : 1. resolveAction()\ncalls extractAction(method, path)
    IntegratedPipeline --> Worklet1 : 2. calls inspect(request)
    Worklet1 ..> Worklet1Result : returns w1 result

    IntegratedPipeline --> Worklet2ProcessClient : 3. calls validate(signedSessionId, method, w1.tokens())
    Worklet2ProcessClient ..> Worklet2Result : returns w2 result

    IntegratedPipeline --> SecureSessionTracker : 4. calls generateSignedSessionId(w1.sessionId())\n5. calls checkValid(signedSessionId, action)\n6. calls commit(signedSessionId, action)
```

---

## 2. Worklet 1 Internal Class Diagram: Lexical, Regex DFA & Tokens

Shows `Worklet1.inspect(HttpRequest)` delegating across its internal sub-components down to the automata algorithms.

```mermaid
classDiagram
    direction TB

    class Worklet1 {
        -Worklet1Config config
        -SessionIdValidator sessionValidator
        -FieldExtractor fieldExtractor
        -Normalizer normalizer
        -SignatureScanner scanner
        -JsonTokenizer tokenizer
        +inspect(HttpRequest request) Worklet1Result
    }

    class SessionIdValidator {
        -DfaMatcher sessionDfa
        +validate(String sessionId) boolean
    }

    class FieldExtractor {
        +extract(HttpRequest req) ExtractedFields
        +extractSessionId(HttpRequest req) String
    }

    class Normalizer {
        +normalize(String input) String
        +urlDecode(String input) String
        +collapseWhitespace(String input) String
    }

    class SignatureScanner {
        -List~CompiledRule~ compiledRules
        +scan(String input, String fieldName) ScanResult
    }

    class CompiledRule {
        -String id
        -String description
        -DfaMatcher dfa
        +matches(String input) boolean
    }

    class DfaMatcher {
        -DFA dfa
        +matches(String input) boolean
    }

    class DFA {
        -int startState
        -Set~Integer~ acceptStates
        -Map~Pair, Integer~ transitions
        +step(int state, char c) int
        +isAccept(int state) boolean
    }

    class Thompson {
        +compile(RegexNode ast) NFA
    }

    class SubsetConstruction {
        +convert(NFA nfa) DFA
    }

    class HopcroftMinimizer {
        +minimize(DFA dfa) DFA
    }

    class JsonTokenizer {
        +tokenize(String json) List~JsonToken~
    }

    class JsonToken {
        -TokenType type
        -String value
        -int position
        +getType() TokenType
        +getValue() String
        +getPosition() int
    }

    Worklet1 --> Worklet1Config
    Worklet1 --> SessionIdValidator
    Worklet1 --> FieldExtractor
    Worklet1 --> Normalizer
    Worklet1 --> SignatureScanner
    Worklet1 --> JsonTokenizer
    Worklet1 ..> HttpRequest : inspects
    Worklet1 ..> Worklet1Result : produces
    %% Algorithms
    class Thompson {
        +compile(RegexNode ast)$ NFA
    }
    class SubsetConstruction {
        +convert(NFA nfa)$ DFA
    }
    class HopcroftMinimizer {
        +minimize(DFA dfa)$ DFA
    }

    Worklet1 *-- SessionIdValidator : validates cookie
    Worklet1 *-- FieldExtractor : parses fields
    Worklet1 *-- Normalizer : canonicalizes payload
    Worklet1 *-- SignatureScanner : scans for SQLi/XSS
    Worklet1 *-- JsonTokenizer : lexical token stream
    JsonTokenizer ..> JsonToken : produces

    SignatureScanner *-- CompiledRule
    CompiledRule *-- DfaMatcher
    SessionIdValidator *-- DfaMatcher
    DfaMatcher *-- DFA

    Thompson ..> DFA : builds via NFA
    Thompson ..> DFA : creates via NFA
    SubsetConstruction ..> DFA : determinizes
    HopcroftMinimizer ..> DFA : minimizes
    JsonTokenizer ..> JsonToken : produces
```

---

## 2. Worklet 2: Structural CYK Parser & CNF Grammar (C++17)

## 3. Worklet 2 Internal Class Diagram: CYK Parser & CNF Grammar (C++17 Subprocess)

Worklet 2 runs as an isolated high-performance subprocess or native client to perform Chomsky Normal Form (CNF) grammar parsing, structural verification, and maximum JSON nesting depth checks.
Shows `Worklet2ProcessClient.validate(...)` communicating over pipes with the native C++17 CYK parser.

```mermaid
classDiagram
    direction TB

    class Worklet2Result {
        -boolean success
        -String reason
        -String rejectReason
        -int parseTimeUs
        +success() boolean
        +reason() String
        +rejectReason() String
        +parseTimeUs() int
        +ok() Worklet2Result$
        +reject(String reason, String detail)$ Worklet2Result
    }

    class Worklet2ProcessClient {
        -String executablePath
        -int maxDepth
        -Process process
        -BufferedWriter stdinWriter
        -BufferedReader stdoutReader
        +validate(String sessionId, String method, List~JsonToken~ tokens) Worklet2Result
        -checkNestingDepth(List~JsonToken~ tokens) int
        -callSubprocess(String jsonPayload) String
    }

    class TokenBridge {
        +tokensToJson(List~JsonToken~ tokens)$ String
        +tokensToW2Format(List~JsonToken~ tokens)$ String
    }

    %% C++ Subprocess Internal Components
    class Worklet2Result {
        +boolean success
        +String reason
        +String rejectReason
        +int parseTimeUs
    }

    %% Native C++ Subprocess
    class W2Main {
        <<C++ Binary (w2_parser.exe)>>
        +main(int argc, char** argv) int
        +processPipeMode() void
    }

    class CYKParser {
        <<C++ Engine>>
        -CNFGrammar grammar
        -int maxTokens
        +parse(vector~Token~ tokens) bool
        +buildParseTree() ParseTree
    }

    class CNFGrammar {
        <<C++ Engine>>
        -vector~Production~ rules
        -unordered_map~string, vector~ terminalRules
        -unordered_map~Pair, vector~ nonTerminalRules
        +loadFromFile(string path) void
        +getStartSymbol() string
    }

    class W2Main {
        <<C++ Entry Point>>
        +main(int argc, char** argv) int
        +processPipeMode() void
    }

    Worklet2ProcessClient ..> TokenBridge : converts tokens
    Worklet2ProcessClient ..> Worklet2Result : produces
    Worklet2ProcessClient ..> W2Main : IPC stdin/stdout
    W2Main --> CYKParser : executes
    CYKParser --> CNFGrammar : references rules
    Worklet2ProcessClient ..> TokenBridge : transforms JsonToken list to JSON
    Worklet2ProcessClient ..> Worklet2Result : generates
    Worklet2ProcessClient ..> W2Main : IPC stdin (token stream) / stdout (verdict)
    W2Main --> CYKParser : executes parse()
    CYKParser --> CNFGrammar : queries CNF production rules
```

---

## 3. Worklet 3: Stateful Turing Machine & Cryptographic Session Engine

## 4. Worklet 3 Internal Class Diagram: Stateful TM & AES Tape

Worklet 3 manages multi-user stateful session tracking through a mathematically verified Turing Machine, HMAC-SHA256 session token authentication, in-memory SHA-256 rulebook integrity validation, and AES-128 encrypted tape memory.
Shows the complete call chain inside Worklet 3 when `checkValid()` and `commit()` are called:
`SecureSessionTracker` $\rightarrow$ `SessionTracker` $\rightarrow$ `TuringMachine` $\rightarrow$ `SecureRuleBook` & `SecureTape`.

```mermaid
classDiagram
    direction TB

    class ActionExtractor {
        +extractAction(String method, String path)$ String
        +extractSessionId(String authHeader, Map cookies)$ String
    }

    class SecureSessionTracker {
        -SessionTracker sessionTracker
        -SessionTokenVerifier tokenVerifier
        +generateSignedSessionId(String rawId) String
        +checkValid(String signedSessionId, String action) boolean
        +commit(String signedSessionId, String action) void
        +getCurrentState(String signedSessionId) String
        +generateSignedSessionId(String rawId) String
        +getTokenVerifier() SessionTokenVerifier
        +getSessionTracker() SessionTracker
    }

    class SessionTokenVerifier {
        -SecretKeySpec secretKey
        +signToken(String rawId) String
        +verifySignature(String token) boolean
        +extractRawId(String token) String
    }

    class SessionTracker {
        -int MAX_SESSIONS
        -ConcurrentHashMap~String, TuringMachine~ sessions
        +checkValid(String rawSessionId, String action) boolean
        +commit(String rawSessionId, String action) void
        +getCurrentState(String rawSessionId) String
        +clearSession(String rawSessionId) void
        +clearAllSessions() void
        -createNewSession(String rawSessionId) TuringMachine
    }

    class TuringMachine {
        -String currentState
        -SecureRuleBook ruleBook
        -SecureTape tape
        +checkValid(String inputAction) boolean
        +commit(String inputAction) void
        +getCurrentState() String
        +getTape() SecureTape
        +getRuleBook() SecureRuleBook
    }

    class SecureRuleBook {
        -Map~TransitionKey, String~ rules
        -String EXPECTED_HASH
        +getNextState(String currentState, String inputAction) String
        +verifyIntegrity() boolean
        -calculateRuleBookHash() String
    }

    class TransitionKey {
        -String state
        -String action
        +equals(Object obj) boolean
        +hashCode() int
    }

    class SecureTape {
        -Tape rawTape
        -SecretKeySpec secretKey
        -Cipher cipher
        +write(String data) void
        +read() String
        +moveRight() void
        +moveLeft() void
        -encrypt(String data) String
        -decrypt(String encryptedData) String
    }

    class Tape {
        -TapeNode head
        +write(String data) void
        +read() String
        +moveRight() void
        +moveLeft() void
    }

    class TapeNode {
        -String data
        -TapeNode prev
        -TapeNode next
    }

    SecureSessionTracker *-- SessionTokenVerifier
    SecureSessionTracker *-- SessionTracker
    SessionTracker o-- TuringMachine : multi-tenant map
    TuringMachine *-- SecureRuleBook
    TuringMachine *-- SecureTape
    %% Method Call Chain
    SecureSessionTracker --> SessionTokenVerifier : 1. verifySignature()\n2. extractRawId()
    SecureSessionTracker --> SessionTracker : 3. checkValid(rawId, action)\n4. commit(rawId, action)

    SessionTracker o-- TuringMachine : retrieves or creates tm per session
    SessionTracker ..> TuringMachine : calls tm.checkValid(action)\ncalls tm.commit(action)

    TuringMachine --> SecureRuleBook : calls getNextState(currentState, action)
    SecureRuleBook o-- TransitionKey : lookup key
    SecureTape *-- Tape : wraps with AES-128
    Tape *-- TapeNode : doubly-linked list

    TuringMachine --> SecureTape : calls moveRight()/moveLeft()\ncalls write(state)
    SecureTape *-- Tape : encrypts data (AES-128)\ncalls rawTape.write(ciphertext)
    Tape *-- TapeNode : updates head.data in Doubly Linked List
```

---

## 4. Master Integration: `IntegratedPipeline` Calling All 3 Worklets

## 5. End-to-End Sequence of Method Invocations

This diagram demonstrates how [`IntegratedPipeline.java`](file:///e:/Kartikey%20A%20Dhale/Miniproject/WAF/WAF-Firewall/src/main/java/com/wafgateway/pipeline/IntegratedPipeline.java) serves as the central brain orchestrating the 3 worklets in sequence for every HTTP request.
For a quick reference of the exact execution sequence triggered by `IntegratedPipeline.process(HttpRequest)`:

```mermaid
classDiagram
    direction TB
sequenceDiagram
    autonumber
    actor Client
    participant IP as IntegratedPipeline
    participant AE as ActionExtractor
    participant W1 as Worklet1
    participant W2 as Worklet2ProcessClient
    participant SST as SecureSessionTracker
    participant STV as SessionTokenVerifier
    participant ST as SessionTracker
    participant TM as TuringMachine
    participant SRB as SecureRuleBook
    participant STape as SecureTape

    class WafReverseProxyServer {
        -int proxyPort
        -String targetBaseUrl
        -IntegratedPipeline pipeline
        -HttpClient httpClient
        +main(String[] args)$ void
        +start() void
        -handleProxyRequest(HttpExchange exchange) void
    }
    Client->>IP: process(request)
    IP->>AE: extractAction(method, path)
    AE-->>IP: "POST_LOGIN" (action)

    class IntegratedPipeline {
        -Worklet1 worklet1
        -Worklet2ProcessClient worklet2
        -SecureSessionTracker w3SessionTracker
        +process(HttpRequest request) PipelineDecision
        -resolveAction(HttpRequest request) String
        +getW3SessionTracker() SecureSessionTracker
    }
    %% Worklet 1
    IP->>W1: inspect(request)
    W1-->>IP: Worklet1Result(blocked=false, sessionId="raw123", tokens=[...])

    class PipelineDecision {
        <<record>>
        -boolean allowed
        -String stage
        -String sessionId
        -String action
        -String details
        +allowed() boolean
        +stage() String
        +sessionId() String
        +action() String
        +details() String
    }
    %% Token Signing
    IP->>SST: generateSignedSessionId("raw123")
    SST->>STV: signToken("raw123")
    STV-->>IP: "raw123.hmacSignature"

    class ActionExtractor {
        +extractAction(String method, String path)$ String
    }
    %% Worklet 2
    opt tokens not empty
        IP->>W2: validate(signedSessionId, method, tokens)
        W2-->>IP: Worklet2Result(success=true)
    end

    class Worklet1 {
        +inspect(HttpRequest request) Worklet1Result
    }
    %% Worklet 3 Verification
    IP->>SST: checkValid(signedSessionId, action)
    SST->>STV: verifySignature(signedSessionId)
    STV-->>SST: true
    SST->>STV: extractRawId(signedSessionId)
    STV-->>SST: "raw123"
    SST->>ST: checkValid("raw123", action)
    ST->>TM: checkValid(action)
    TM->>SRB: getNextState(currentState, action)
    SRB-->>TM: "AUTH:RIGHT"
    TM-->>ST: true
    ST-->>SST: true
    SST-->>IP: true (validTransition)

    class Worklet2ProcessClient {
        +validate(String sid, String method, List tokens) Worklet2Result
    }
    %% Worklet 3 Commit
    IP->>SST: commit(signedSessionId, action)
    SST->>ST: commit("raw123", action)
    ST->>TM: commit(action)
    TM->>STape: moveRight()
    TM->>STape: write("AUTH")
    Note over STape: AES-128 Encrypt & update TapeNode
    TM-->>IP: state updated to AUTH

    class SecureSessionTracker {
        +generateSignedSessionId(String rawId) String
        +checkValid(String signedId, String action) boolean
        +commit(String signedId, String action) void
        +getCurrentState(String signedId) String
    }

    WafReverseProxyServer --> IntegratedPipeline : 1. Dispatches incoming HTTP requests
    IntegratedPipeline ..> PipelineDecision : returns verdict (ALLOWED / BLOCKED)

    %% Coordination Sequence
    IntegratedPipeline ..> ActionExtractor : 2. Extracts Action (POST_LOGIN, POST_CART, etc.)
    IntegratedPipeline --> Worklet1 : 3. Invokes inspect() [Lexical / Regex / Signatures]
    IntegratedPipeline --> Worklet2ProcessClient : 4. Invokes validate() [CYK / CNF / Depth]
    IntegratedPipeline --> SecureSessionTracker : 5. Invokes checkValid() & commit() [Stateful TM / Tape]
    IP-->>Client: PipelineDecision(allowed=true, stage="Pipeline Complete")
```
