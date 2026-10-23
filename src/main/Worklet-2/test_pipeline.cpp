#include <iostream>
#include <vector>
#include <cassert>
#include "worklet2/common/Token.hpp"
#include "worklet2/common/ValidationResult.hpp"
#include "worklet2/pipeline/Worklet2Gateway.hpp"

using namespace worklet2;

void testValidObject() {
    Worklet2Gateway gateway;
    bool callbackInvoked = false;
    std::string capturedSession = "";
    std::string capturedAction = "";

    gateway.setWorklet3Callback([&](const std::string& sid, const std::string& act) -> bool {
        callbackInvoked = true;
        capturedSession = sid;
        capturedAction = act;
        return true;
    });

    std::vector<Token> tokens = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "action", 1, 2},
        {TokenType::COLON, ":", 1, 8},
        {TokenType::STRING, "CART", 1, 10},
        {TokenType::RBRACE, "}", 1, 16}
    };

    // Passing HTTP Method "POST"
    ValidationResult res = gateway.validateAndProcess("session_001", "POST", tokens);
    assert(res.success == true);
    assert(res.sessionId == "session_001");
    assert(res.action == "POST_CART");
    assert(callbackInvoked == true);
    assert(capturedSession == "session_001");
    assert(capturedAction == "POST_CART");
    std::cout << "[PASS] testValidObject & Worklet 3 (POST_CART) Callback" << std::endl;
}

void testMalformedInput() {
    Worklet2Gateway gateway;
    std::vector<Token> tokens = {
        {TokenType::LBRACE, "{", 1, 1},
        {TokenType::STRING, "key", 1, 2}
    };

    ValidationResult res = gateway.validateAndProcess("session_002", "POST", tokens);
    assert(res.success == false);
    assert(!res.rejectReason.empty());
    std::cout << "[PASS] testMalformedInput" << std::endl;
}

void testJsonBombProtection() {
    Worklet2Gateway gateway;
    std::vector<Token> tokens;
    for (int i = 0; i < 600; ++i) {
        tokens.push_back({TokenType::LBRACE, "{", 1, 1});
    }

    ValidationResult res = gateway.validateAndProcess("session_bomb", "POST", tokens);
    assert(res.success == false);
    assert(res.rejectReason.find("Token limit exceeded") != std::string::npos);
    std::cout << "[PASS] testJsonBombProtection (500 Limit)" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << " RUNNING MEMBER 4 PIPELINE QA TESTS     " << std::endl;
    std::cout << "========================================" << std::endl;

    testValidObject();
    testMalformedInput();
    testJsonBombProtection();

    std::cout << "========================================" << std::endl;
    std::cout << " ALL MEMBER 4 TESTS PASSED SUCCESSFULLY!" << std::endl;
    std::cout << "========================================" << std::endl;
    return 0;
}
