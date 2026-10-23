#pragma once
#include "worklet2/common/Token.hpp"
#include "worklet2/common/ValidationResult.hpp"
#include <string>
#include <vector>

namespace worklet2::bridge {

class Worklet1Adapter {
public:
    // Parses token stream representation from Worklet 1
    static std::vector<Token> parseTokensFromJson(const std::string& jsonTokens);

    // Directly tokenizes raw JSON payload into Worklet 2 Tokens (Worklet 1 conversion fallback)
    static std::vector<Token> tokenizeRawJson(const std::string& rawJson);

    // Serializes ValidationResult to JSON format for Worklet 3 consumption
    static std::string resultToJson(const ValidationResult& result);
};

} // namespace worklet2::bridge
