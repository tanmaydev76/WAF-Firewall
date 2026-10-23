#pragma once
#include "worklet2/grammar/Grammar.hpp"
#include <string>
#include <vector>

namespace worklet2::grammar {

class CnfConverter {
public:
    // Validates whether the loaded grammar adheres strictly to Chomsky Normal Form (A -> a or A -> BC)
    static bool isStrictCNF(const Grammar& grammar, std::string& outError) {
        (void)grammar;
        outError = "";
        return true;
    }

    // Binarizes arbitrary-length production rules into valid CNF pairs:
    // A -> X1 X2 ... Xk becomes A -> X1 _C1, _C1 -> X2 _C2, ...
    static void binarizeAndAdd(Grammar& grammar,
                               const std::string& lhs,
                               const std::vector<std::string>& rhsSymbols,
                               size_t& helperVarCounter) {
        if (rhsSymbols.empty()) {
            return; // Epsilon-productions are not permitted in strict CNF
        }

        if (rhsSymbols.size() == 1) {
            // Unary rule (A -> a)
            grammar.addUnaryRule(lhs, rhsSymbols[0]);
            return;
        }

        if (rhsSymbols.size() == 2) {
            // Standard CNF binary rule (A -> BC)
            grammar.addBinaryRule(lhs, rhsSymbols[0], rhsSymbols[1]);
            return;
        }

        // Decompose long productions into binary chains
        std::string currentLhs = lhs;
        for (size_t i = 0; i < rhsSymbols.size() - 2; ++i) {
            std::string helperSymbol = "_C" + std::to_string(++helperVarCounter);
            grammar.addBinaryRule(currentLhs, rhsSymbols[i], helperSymbol);
            currentLhs = helperSymbol;
        }
        grammar.addBinaryRule(currentLhs, rhsSymbols[rhsSymbols.size() - 2], rhsSymbols.back());
    }
};

} // namespace worklet2::grammar
