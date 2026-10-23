#pragma once
#include "worklet2/tree/ParseTreeNode.hpp"
#include <vector>
#include <string>

namespace worklet2::tree {

class TreeChecker {
public:
    // Ensures brackets ({}, []) match and balance properly
    static bool tagsMatch(const ParseTreeNode& root);

    // Calculates maximum nesting depth of objects (JSON bomb defense)
    static int nestingDepth(const ParseTreeNode& root);

private:
    static void collectDelimiters(const ParseTreeNode& node, std::vector<std::string>& delimiters);
    static int calculateDepth(const ParseTreeNode& node, int currentDepth);
};

} // namespace worklet2::tree
