#pragma once
#include <string>
#include <vector>
#include <unordered_map>
#include <unordered_set>
#include <utility>

namespace worklet2::grammar {

struct SymbolPairHash {
    std::size_t operator()(const std::pair<std::string, std::string>& pair) const {
        std::size_t h1 = std::hash<std::string>()(pair.first);
        std::size_t h2 = std::hash<std::string>()(pair.second);
        return h1 ^ (h2 << 1);
    }
};

class Grammar {
public:
    Grammar() = default;

    void setStartSymbol(const std::string& startSymbol);
    const std::string& getStartSymbol() const;

    void addUnaryRule(const std::string& lhs, const std::string& terminal);
    void addBinaryRule(const std::string& lhs, const std::string& leftRhs, const std::string& rightRhs);

    // O(1) lookups used by Member 2 CYK table
    const std::vector<std::string>& getUnary(const std::string& terminal) const;
    const std::vector<std::string>& getBinary(const std::string& leftRhs, const std::string& rightRhs) const;

    bool isValidTerminal(const std::string& terminal) const;
    bool isValidNonTerminal(const std::string& nonTerminal) const;

    void clear();

private:
    std::string startSymbol_{"S"};
    std::unordered_map<std::string, std::vector<std::string>> unaryRules_;
    std::unordered_map<std::pair<std::string, std::string>, std::vector<std::string>, SymbolPairHash> binaryRules_;
    std::unordered_set<std::string> terminals_;
    std::unordered_set<std::string> nonTerminals_;

    static const std::vector<std::string> emptyVector_;
};

} // namespace worklet2::grammar