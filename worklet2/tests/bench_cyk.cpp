#include <iostream>
#include <vector>
#include <chrono>
#include <iomanip>
#include "worklet2/common/Token.hpp"
#include "worklet2/grammar/Grammar.hpp"
#include "worklet2/grammar/GrammarConfig.hpp"
#include "worklet2/cyk/CYKTable.hpp"

using namespace worklet2;

// Helper to generate a valid JSON object token stream with k key-value pairs:
// {"k1":"v1", "k2":"v2", ...}
std::vector<Token> generateObjectTokens(int pairCount) {
    std::vector<Token> tokens;
    tokens.push_back({TokenType::LBRACE, "{", 1, 1});
    for (int i = 0; i < pairCount; ++i) {
        if (i > 0) {
            tokens.push_back({TokenType::COMMA, ",", 1, 1});
        }
        tokens.push_back({TokenType::STRING, "key" + std::to_string(i), 1, 1});
        tokens.push_back({TokenType::COLON, ":", 1, 1});
        tokens.push_back({TokenType::STRING, "val" + std::to_string(i), 1, 1});
    }
    tokens.push_back({TokenType::RBRACE, "}", 1, 1});
    return tokens;
}

void benchmarkCYK(const grammar::Grammar& grammar, int pairCount, int iterations) {
    auto tokens = generateObjectTokens(pairCount);
    size_t n = tokens.size();

    // Warm-up run
    cyk::CYKTable warmup(tokens, grammar);
    bool accepted = warmup.accepted(grammar.getStartSymbol());

    auto start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < iterations; ++i) {
        cyk::CYKTable table(tokens, grammar);
    }
    auto end = std::chrono::high_resolution_clock::now();

    auto totalDurationUs = std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();
    double avgUs = static_cast<double>(totalDurationUs) / iterations;

    std::cout << "| " << std::setw(8) << pairCount
              << " | " << std::setw(12) << n
              << " | " << std::setw(12) << (accepted ? "ACCEPTED" : "REJECTED")
              << " | " << std::setw(12) << iterations
              << " | " << std::setw(14) << std::fixed << std::setprecision(2) << avgUs
              << " |" << std::endl;
}

int main() {
    grammar::Grammar grammar;
    grammar::GrammarConfig::loadDefaultRules(grammar);

    std::cout << "==========================================================================" << std::endl;
    std::cout << "       MEMBER 2: CYK DYNAMIC PROGRAMMING ENGINE BENCHMARK                 " << std::endl;
    std::cout << "       Complexity Target: O(n^3 * |G|)                                     " << std::endl;
    std::cout << "==========================================================================" << std::endl;
    std::cout << "| Pairs    | Tokens (n)   | Status       | Iterations   | Avg Time (us)  |" << std::endl;
    std::cout << "|----------|--------------|--------------|--------------|----------------|" << std::endl;

    benchmarkCYK(grammar, 1, 100);
    benchmarkCYK(grammar, 2, 100);
    benchmarkCYK(grammar, 3, 50);
    benchmarkCYK(grammar, 5, 20);
    benchmarkCYK(grammar, 8, 10);
    benchmarkCYK(grammar, 12, 5);

    std::cout << "==========================================================================" << std::endl;
    std::cout << " BENCHMARK COMPLETED SUCCESSFULLY                                         " << std::endl;
    std::cout << "==========================================================================" << std::endl;
    return 0;
}
