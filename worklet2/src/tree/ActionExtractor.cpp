#include "worklet2/tree/ActionExtractor.hpp"
#include <algorithm>
#include <cctype>

namespace worklet2::tree {

std::string ActionExtractor::stripQuotes(const std::string& str) {
    if (str.size() >= 2 && str.front() == '"' && str.back() == '"') {
        return str.substr(1, str.size() - 2);
    }
    return str;
}

static std::string getLeafLexeme(const ParseTreeNode* node) {
    if (!node) return "";
    if (node->isTerminal) return node->lexeme;
    if (node->left) {
        std::string s = getLeafLexeme(node->left.get());
        if (!s.empty()) return s;
    }
    if (node->right) {
        return getLeafLexeme(node->right.get());
    }
    return "";
}

void ActionExtractor::findPairs(const ParseTreeNode& node, std::map<std::string, std::string>& outPairs) {
    // Check if this node is a pair (L -> K Y or P -> K Y or left is K and right is Y)
    if (node.left && node.right &&
        node.left->symbol == "K" && node.right->symbol == "Y") {
        std::string rawKey = getLeafLexeme(node.left.get());
        std::string cleanKey = stripQuotes(rawKey);

        // Under Y (Y -> D Value), right child is Value
        std::string rawVal = "";
        if (node.right->right) {
            rawVal = getLeafLexeme(node.right->right.get());
        } else {
            rawVal = getLeafLexeme(node.right.get());
        }
        std::string cleanVal = stripQuotes(rawVal);

        if (!cleanKey.empty()) {
            outPairs[cleanKey] = cleanVal;
            std::string lowerKey = cleanKey;
            std::transform(lowerKey.begin(), lowerKey.end(), lowerKey.begin(), ::tolower);
            outPairs[lowerKey] = cleanVal;
        }
    }

    if (node.left) {
        findPairs(*node.left, outPairs);
    }
    if (node.right) {
        findPairs(*node.right, outPairs);
    }
}

std::map<std::string, std::string> ActionExtractor::extractPairs(const ParseTreeNode& root) {
    std::map<std::string, std::string> pairs;
    findPairs(root, pairs);
    return pairs;
}

std::optional<std::string> ActionExtractor::getAction(const std::map<std::string, std::string>& pairs) {
    auto it = pairs.find("action");
    if (it != pairs.end() && !it->second.empty()) {
        return it->second;
    }
    it = pairs.find("ACTION");
    if (it != pairs.end() && !it->second.empty()) {
        return it->second;
    }
    it = pairs.find("Action");
    if (it != pairs.end() && !it->second.empty()) {
        return it->second;
    }
    return std::nullopt;
}

} // namespace worklet2::tree
