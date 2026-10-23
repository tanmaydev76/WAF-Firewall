#include "worklet2/pipeline/StructuralValidator.hpp"
#include "worklet2/grammar/GrammarConfig.hpp"
#include "worklet2/grammar/Grammar.hpp"
#include "worklet2/common/Token.hpp"
#include "worklet2/bridge/Worklet1Adapter.hpp"
#include <iostream>
#include <string>
#include <vector>

/**
 * Worklet 2 Standalone Subprocess Entry Point (w2_parser).
 *
 * Implements the Gateway-Orchestrated contract:
 *   stdin  : token JSON stream from Worklet 1
 *   stdout : "true\n" or "false\n"
 *   exit 0 : normal completion
 */
int main() {
    std::string json;
    std::string line;
    while (std::getline(std::cin, line)) {
        json += line + "\n";
    }

    if (json.empty()) {
        std::cout << "false" << std::endl;
        std::cout.flush();
        return 0;
    }

    std::vector<worklet2::Token> tokens;
    try {
        tokens = worklet2::bridge::Worklet1Adapter::parseTokensFromJson(json);
    } catch (...) {
        std::cout << "false" << std::endl;
        std::cout.flush();
        return 0;
    }

    worklet2::grammar::Grammar grammar;
    std::string err;
    if (!worklet2::grammar::GrammarConfig::loadFromFile("config/cfg_rules.json", grammar, err) &&
        !worklet2::grammar::GrammarConfig::loadFromFile("cfg_rules.json", grammar, err)) {
        worklet2::grammar::GrammarConfig::loadDefaultRules(grammar);
    }

    // Pure structural validation (token limit <= 500, CYK parse, delimiter check, depth limit <= 3)
    worklet2::StructuralValidator validator(grammar, 3);
    auto res = validator.validate(tokens);

    std::cout << (res.success ? "true" : "false") << std::endl;
    std::cout.flush();
    return 0;
}
