#pragma once
#include "worklet2/common/Token.hpp"
#include "worklet2/common/ValidationResult.hpp"
#include "worklet2/grammar/Grammar.hpp"
#include "worklet2/cyk/CYKTable.hpp"
#include "worklet2/tree/TreeBuilder.hpp"
#include "worklet2/tree/TreeChecker.hpp"
#include "worklet2/tree/ActionExtractor.hpp"
#include <vector>
#include <string>

namespace worklet2 {

class StructuralValidator {
public:
    static constexpr size_t MAX_TOKEN_LIMIT = 500;
    static constexpr int DEFAULT_MAX_DEPTH = 3;

    explicit StructuralValidator(const grammar::Grammar& grammar, int maxDepth = DEFAULT_MAX_DEPTH);

    static bool preCheck(const std::vector<Token>& tokens, std::string& outReason);

    ValidationResult validate(const std::vector<Token>& tokens) const;

private:
    const grammar::Grammar& grammar_;
    int maxDepth_{DEFAULT_MAX_DEPTH};
};

} // namespace worklet2
