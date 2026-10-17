#pragma once
#include "worklet2/cyk/RuleMatch.hpp"
#include <unordered_map>
#include <string>

namespace worklet2::cyk {

class Cell {
public:
    std::unordered_map<std::string, RuleMatch> matches;

    void addSymbol(const std::string& symbol, const RuleMatch& match);
    bool hasSymbol(const std::string& symbol) const;
    const RuleMatch* getMatch(const std::string& symbol) const;
    bool empty() const;
    void clear();
};

} // namespace worklet2::cyk
