#include "worklet2/pipeline/StructuralValidator.hpp"

namespace worklet2 {

StructuralValidator::StructuralValidator(const grammar::Grammar& grammar, int maxDepth)
    : grammar_(grammar), maxDepth_(maxDepth) {}

bool StructuralValidator::preCheck(const std::vector<Token>& tokens, std::string& outReason) {
    if (tokens.empty()) {
        outReason = "Reject Reason: Token stream is empty.";
        return false;
    }
    if (tokens.size() > MAX_TOKEN_LIMIT) {
        outReason = "Reject Reason: Token limit exceeded (Potential JSON bomb attack).";
        return false;
    }
    return true;
}

ValidationResult StructuralValidator::validate(const std::vector<Token>& tokens) const {
    ValidationResult result;

    // 1. Pre-check token limits
    std::string rejectReason;
    if (!preCheck(tokens, rejectReason)) {
        result.success = false;
        result.reason = tokens.empty() ? RejectReason::EMPTY_INPUT : RejectReason::TOKEN_LIMIT_EXCEEDED;
        result.rejectReason = rejectReason;
        return result;
    }

    // 2. CYK Parse
    cyk::CYKTable table(tokens, grammar_);
    if (!table.accepted(grammar_.getStartSymbol())) {
        result.success = false;
        result.reason = RejectReason::GRAMMAR_MISMATCH;
        result.rejectReason = "CYK Syntax Error: Input cannot be derived by grammar start symbol.";
        return result;
    }

    // 3. Rebuild Derivation Tree
    auto tree = tree::TreeBuilder::build(table, tokens, grammar_.getStartSymbol());
    if (!tree) {
        result.success = false;
        result.reason = RejectReason::GRAMMAR_MISMATCH;
        result.rejectReason = "Tree Reconstruction Error: Failed to unpack derivation spans.";
        return result;
    }

    // 4. Bracket / Tag Match Check
    if (!tree::TreeChecker::tagsMatch(*tree)) {
        result.success = false;
        result.reason = RejectReason::TAG_MISMATCH;
        result.rejectReason = "Structural Tag Mismatch: Unbalanced braces detected.";
        return result;
    }

    // 5. JSON Bomb Depth Check
    int depth = tree::TreeChecker::nestingDepth(*tree);
    if (depth > maxDepth_) {
        result.success = false;
        result.reason = RejectReason::DEPTH_EXCEEDED;
        result.rejectReason = "Security Error: JSON nesting depth exceeded MAX_DEPTH limit.";
        return result;
    }

    // 6. Semantic Action Extraction
    result.extractedPairs = tree::ActionExtractor::extractPairs(*tree);
    auto actionOpt = tree::ActionExtractor::getAction(result.extractedPairs);
    result.action = actionOpt.value_or("");
    result.tree = std::move(tree);
    result.success = true;
    result.reason = RejectReason::NONE;
    return result;
}

} // namespace worklet2
