#pragma once
#include <string>
#include <cstddef>

namespace worklet2::cyk {

struct RuleMatch {
    bool isTerminal{false};
    size_t split{0};              // Split index k for binary decomposition
    std::string leftSymbol{""};   // Non-terminal B in A -> BC
    std::string rightSymbol{""};  // Non-terminal C in A -> BC
};

} // namespace worklet2::cyk
