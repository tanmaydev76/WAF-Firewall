#pragma once
#include "worklet2/common/Token.hpp"
#include "worklet2/grammar/Grammar.hpp"
#include "worklet2/cyk/Cell.hpp"
#include <vector>

namespace worklet2::cyk {

class CYKTable {
public:
    static constexpr size_t MAX_TOKEN_LIMIT = 500;

    CYKTable(const std::vector<Token>& tokens, const grammar::Grammar& grammar);

    bool accepted(const std::string& startSymbol) const;
    const Cell& cell(size_t start, size_t len) const;
    size_t tokenCount() const;
    bool isValid() const;

private:
    size_t n_{0};
    bool valid_{true};
    std::vector<std::vector<Cell>> table_;

    void fill(const std::vector<Token>& tokens, const grammar::Grammar& grammar);
};

} // namespace worklet2::cyk
