#pragma once
#include <string>
#include <memory>
#include <map>

namespace worklet2 {

struct ParseTreeNode; // Forward declaration for Member 3 AST node

enum class RejectReason {
    NONE,
    EMPTY_INPUT,
    TOKEN_LIMIT_EXCEEDED,
    GRAMMAR_MISMATCH,
    TAG_MISMATCH,
    DEPTH_EXCEEDED,
    DOWNSTREAM_REJECTED
};

struct ValidationResult {
    bool success{false};
    RejectReason reason{RejectReason::NONE};
    std::string sessionId{""};
    std::string action{""};
    std::map<std::string, std::string> extractedPairs;
    std::shared_ptr<ParseTreeNode> tree{nullptr};
    std::string rejectReason{""};
};

} // namespace worklet2
