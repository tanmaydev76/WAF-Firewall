#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <iomanip>
#include "worklet2/common/Token.hpp"
#include "worklet2/common/ValidationResult.hpp"
#include "worklet2/grammar/Grammar.hpp"
#include "worklet2/grammar/GrammarConfig.hpp"
#include "worklet2/pipeline/StructuralValidator.hpp"
#include "worklet2/pipeline/Worklet2Gateway.hpp"
#include "worklet2/bridge/Worklet1Adapter.hpp"

using namespace worklet2;

void printBanner() {
    std::cout << "====================================================================\n";
    std::cout << "   WORKLET 2: CYK STRUCTURAL PARSER & VALIDATION ENGINE DEMO        \n";
    std::cout << "   Chomsky Normal Form | Dynamic Programming | JSON Bomb Defense   \n";
    std::cout << "====================================================================\n\n";
}

void printCaseResult(int index, const std::string& title, const ValidationResult& res) {
    std::cout << "[" << index << "] " << title << "\n";
    std::cout << "    Session ID   : " << (res.sessionId.empty() ? "(none)" : res.sessionId) << "\n";
    std::cout << "    Action       : " << (res.action.empty() ? "(none)" : res.action) << "\n";
    std::cout << "    Verdict      : " << (res.success ? "ALLOWED [PASS]" : "BLOCKED [REJECTED]") << "\n";
    if (!res.success) {
        std::cout << "    Reject Cause : " << res.rejectReason << "\n";
    } else {
        std::cout << "    Extracted KV : ";
        for (const auto& pair : res.extractedPairs) {
            std::cout << pair.first << "=" << pair.second << " ";
        }
        std::cout << "\n";
    }
    std::cout << "    Status       : SUCCESS\n\n";
}

void runDemoPipeline() {
    printBanner();

    // 1. Initialize Grammar and Gateway
    grammar::Grammar grammar;
    std::string err;
    if (!grammar::GrammarConfig::loadFromFile("config/cfg_rules.json", grammar, err) &&
        !grammar::GrammarConfig::loadFromFile("cfg_rules.json", grammar, err)) {
        std::cout << "[INFO] Loading default CNF Grammar rules...\n";
        grammar::GrammarConfig::loadDefaultRules(grammar);
    } else {
        std::cout << "[INFO] Loaded grammar from cfg_rules.json successfully.\n";
    }

    StructuralValidator validator(grammar, 3);
    Worklet2Gateway gateway(validator);

    // Simulated Worklet 3 Turing Machine delegate
    // Allows POST_LOGIN, POST_CART, POST_CHECKOUT; blocks any unauthorized action
    gateway.setWorklet3Callback([](const std::string& sessionId, const std::string& action) -> bool {
        if (action == "POST_CART" || action == "POST_CHECKOUT" || action == "POST_LOGIN") {
            return true;
        }
        return false;
    });

    std::cout << "[INFO] Worklet 3 Turing Machine delegate registered.\n\n";

    // Scenario 1: Valid Cart Request
    std::vector<Token> tokens1 = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "action", 1, 2},
        {TokenType::COLON, ":", 1, 8},
        {TokenType::STRING, "CART", 1, 10},
        {TokenType::RBRACE, "}", 1, 16}
    };
    ValidationResult res1 = gateway.validateAndProcess("session_user_01", "POST", tokens1);
    printCaseResult(1, "Valid JSON Payload: {\"action\": \"CART\"}", res1);

    // Scenario 2: Valid Checkout Request
    std::vector<Token> tokens2 = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "action", 1, 2},
        {TokenType::COLON, ":", 1, 8},
        {TokenType::STRING, "CHECKOUT", 1, 10},
        {TokenType::RBRACE, "}", 1, 20}
    };
    ValidationResult res2 = gateway.validateAndProcess("session_user_01", "POST", tokens2);
    printCaseResult(2, "Valid JSON Payload: {\"action\": \"CHECKOUT\"}", res2);

    // Scenario 3: Malformed Syntax (Unfinished JSON)
    std::vector<Token> tokens3 = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "action", 1, 2}
    };
    ValidationResult res3 = gateway.validateAndProcess("session_user_02", "POST", tokens3);
    printCaseResult(3, "Malformed Syntax: {\"action\" (missing colon and brace)", res3);

    // Scenario 4: JSON Bomb Attack (Depth limit 4 > 3)
    std::vector<Token> tokens4 = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "a", 1, 2},
        {TokenType::COLON, ":", 1, 3},
        {TokenType::LBRACE, "{", 1, 4},
        {TokenType::STRING, "b", 1, 5},
        {TokenType::COLON, ":", 1, 6},
        {TokenType::LBRACE, "{", 1, 7},
        {TokenType::STRING, "c", 1, 8},
        {TokenType::COLON, ":", 1, 9},
        {TokenType::LBRACE, "{", 1, 10},
        {TokenType::STRING, "d", 1, 11},
        {TokenType::COLON, ":", 1, 12},
        {TokenType::STRING, "bomb", 1, 13},
        {TokenType::RBRACE, "}", 1, 14},
        {TokenType::RBRACE, "}", 1, 15},
        {TokenType::RBRACE, "}", 1, 16},
        {TokenType::RBRACE, "}", 1, 17}
    };
    ValidationResult res4 = gateway.validateAndProcess("session_attacker", "POST", tokens4);
    printCaseResult(4, "JSON Bomb Nesting Attack (Depth 4 > Max Limit 3)", res4);

    // Scenario 5: Token Bomb Attack (>500 tokens)
    std::vector<Token> tokens5;
    for (int i = 0; i < 550; ++i) {
        tokens5.push_back({TokenType::LBRACE, "{", 1, 1});
    }
    ValidationResult res5 = gateway.validateAndProcess("session_flood", "POST", tokens5);
    printCaseResult(5, "Token Flood Attack (550 tokens > Max Limit 500)", res5);

    // Scenario 6: Downstream Worklet 3 State Violation
    std::vector<Token> tokens6 = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "action", 1, 2},
        {TokenType::COLON, ":", 1, 8},
        {TokenType::STRING, "ADMIN_DELETE_ALL", 1, 10},
        {TokenType::RBRACE, "}", 1, 28}
    };
    ValidationResult res6 = gateway.validateAndProcess("session_user_03", "POST", tokens6);
    printCaseResult(6, "Worklet 3 TM State Rejection: POST_ADMIN_DELETE_ALL", res6);

    std::cout << "====================================================================\n";
    std::cout << " PIPELINE ORCHESTRATION DEMO COMPLETED SUCCESSFULLY                 \n";
    std::cout << "====================================================================\n";
}

int main(int argc, char* argv[]) {
    if (argc > 1) {
        std::string mode = argv[1];
        if (mode == "--pipe") {
            // Read JSON from stdin, process, print result JSON to stdout
            std::string line;
            std::string content;
            while (std::getline(std::cin, line)) {
                content += line + "\n";
            }
            auto tokens = bridge::Worklet1Adapter::parseTokensFromJson(content);
            std::string sessionId = (argc > 2) ? argv[2] : "pipe_session";
            std::string method = (argc > 3) ? argv[3] : "POST";

            Worklet2Gateway gateway;
            ValidationResult res = gateway.validateAndProcess(sessionId, method, tokens);
            std::cout << bridge::Worklet1Adapter::resultToJson(res) << std::endl;
            return res.success ? 0 : 1;
        } else if (mode == "--json" && argc > 2) {
            std::string jsonStr = argv[2];
            std::string sessionId = (argc > 3) ? argv[3] : "cli_session";
            std::string method = (argc > 4) ? argv[4] : "POST";

            auto tokens = bridge::Worklet1Adapter::tokenizeRawJson(jsonStr);
            Worklet2Gateway gateway;
            ValidationResult res = gateway.validateAndProcess(sessionId, method, tokens);
            std::cout << bridge::Worklet1Adapter::resultToJson(res) << std::endl;
            return res.success ? 0 : 1;
        }
    }

    runDemoPipeline();
    return 0;
}
