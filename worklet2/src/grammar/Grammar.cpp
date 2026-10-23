#include "worklet2/grammar/Grammar.hpp"
#include <algorithm>

namespace worklet2::grammar {

const std::vector<std::string> Grammar::emptyVector_{};

void Grammar::setStartSymbol(const std::string& startSymbol) {
    startSymbol_ = startSymbol;
    nonTerminals_.insert(startSymbol);
}

const std::string& Grammar::getStartSymbol() const {
    return startSymbol_;
}

void Grammar::addUnaryRule(const std::string& lhs, const std::string& terminal) {
    nonTerminals_.insert(lhs);
    terminals_.insert(terminal);

    auto& targets = unaryRules_[terminal];
    if (std::find(targets.begin(), targets.end(), lhs) == targets.end()) {
        targets.push_back(lhs);
    }
}

void Grammar::addBinaryRule(const std::string& lhs, const std::string& leftRhs, const std::string& rightRhs) {
    nonTerminals_.insert(lhs);
    nonTerminals_.insert(leftRhs);
    nonTerminals_.insert(rightRhs);

    auto key = std::make_pair(leftRhs, rightRhs);
    auto& targets = binaryRules_[key];
    if (std::find(targets.begin(), targets.end(), lhs) == targets.end()) {
        targets.push_back(lhs);
    }
}

const std::vector<std::string>& Grammar::getUnary(const std::string& terminal) const {
    auto it = unaryRules_.find(terminal);
    if (it != unaryRules_.end()) {
        return it->second;
    }
    return emptyVector_;
}

const std::vector<std::string>& Grammar::getBinary(const std::string& leftRhs, const std::string& rightRhs) const {
    auto it = binaryRules_.find({leftRhs, rightRhs});
    if (it != binaryRules_.end()) {
        return it->second;
    }
    return emptyVector_;
}

bool Grammar::isValidTerminal(const std::string& terminal) const {
    return terminals_.find(terminal) != terminals_.end();
}

bool Grammar::isValidNonTerminal(const std::string& nonTerminal) const {
    return nonTerminals_.find(nonTerminal) != nonTerminals_.end();
}

void Grammar::clear() {
    startSymbol_ = "S";
    unaryRules_.clear();
    binaryRules_.clear();
    terminals_.clear();
    nonTerminals_.clear();
}

} // namespace worklet2::grammar
