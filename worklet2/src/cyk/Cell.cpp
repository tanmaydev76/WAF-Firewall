#include "worklet2/cyk/Cell.hpp"

namespace worklet2::cyk {

void Cell::addSymbol(const std::string& symbol, const RuleMatch& match) {
    if (matches.find(symbol) == matches.end()) {
        matches[symbol] = match;
    }
}

bool Cell::hasSymbol(const std::string& symbol) const {
    return matches.find(symbol) != matches.end();
}

const RuleMatch* Cell::getMatch(const std::string& symbol) const {
    auto it = matches.find(symbol);
    return (it != matches.end()) ? &(it->second) : nullptr;
}

bool Cell::empty() const {
    return matches.empty();
}

void Cell::clear() {
    matches.clear();
}

} // namespace worklet2::cyk
