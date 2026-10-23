#pragma once
#include "worklet2/tree/ParseTreeNode.hpp"
#include <map>
#include <string>

#if __has_include(<optional>)
#include <optional>
#elif __has_include(<experimental/optional>)
#include <experimental/optional>
namespace std { using experimental::optional; using experimental::nullopt; }
#endif

namespace worklet2::tree {

class ActionExtractor {
public:
    static std::map<std::string, std::string> extractPairs(const ParseTreeNode& root);
    static std::optional<std::string> getAction(const std::map<std::string, std::string>& pairs);

private:
    static std::string stripQuotes(const std::string& str);
    static void findPairs(const ParseTreeNode& node, std::map<std::string, std::string>& outPairs);
};

} // namespace worklet2::tree
