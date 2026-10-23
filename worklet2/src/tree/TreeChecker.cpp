#include "worklet2/tree/TreeChecker.hpp"
#include <stack>
#include <vector>
#include <algorithm>

namespace worklet2::tree {

void TreeChecker::collectDelimiters(const ParseTreeNode& node, std::vector<std::string>& delimiters) {
    if (node.isTerminal) {
        if (node.lexeme == "{" || node.lexeme == "}" ||
            node.lexeme == "[" || node.lexeme == "]") {
            delimiters.push_back(node.lexeme);
        }
        return;
    }
    if (node.left) {
        collectDelimiters(*node.left, delimiters);
    }
    if (node.right) {
        collectDelimiters(*node.right, delimiters);
    }
}

bool TreeChecker::tagsMatch(const ParseTreeNode& root) {
    std::vector<std::string> delimiters;
    collectDelimiters(root, delimiters);

    std::stack<std::string> stk;
    for (const auto& delim : delimiters) {
        if (delim == "{" || delim == "[") {
            stk.push(delim);
        } else if (delim == "}") {
            if (stk.empty() || stk.top() != "{") {
                return false;
            }
            stk.pop();
        } else if (delim == "]") {
            if (stk.empty() || stk.top() != "[") {
                return false;
            }
            stk.pop();
        }
    }
    return stk.empty();
}

int TreeChecker::calculateDepth(const ParseTreeNode& node, int currentDepth) {
    int maxSubDepth = currentDepth;
    // An object scope starts at root "S" or nested "Value" deriving "B M"
    bool isObjectScope = (node.symbol == "S" ||
                         (node.symbol == "Value" && node.left && node.left->symbol == "B"));
    int newDepth = isObjectScope ? (currentDepth + 1) : currentDepth;
    maxSubDepth = std::max(maxSubDepth, newDepth);

    if (node.left) {
        maxSubDepth = std::max(maxSubDepth, calculateDepth(*node.left, newDepth));
    }
    if (node.right) {
        maxSubDepth = std::max(maxSubDepth, calculateDepth(*node.right, newDepth));
    }
    return maxSubDepth;
}

int TreeChecker::nestingDepth(const ParseTreeNode& root) {
    return calculateDepth(root, 0);
}

} // namespace worklet2::tree
