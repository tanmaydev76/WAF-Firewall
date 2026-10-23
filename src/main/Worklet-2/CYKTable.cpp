#include "worklet2/cyk/CYKTable.hpp"

namespace worklet2::cyk {

static std::string tokenTypeToString(TokenType type) {
    switch (type) {
        case TokenType::LBRACE:   return "LBRACE";
        case TokenType::RBRACE:   return "RBRACE";
        case TokenType::COLON:    return "COLON";
        case TokenType::COMMA:    return "COMMA";
        case TokenType::STRING:   return "STRING";
        default:                  return "UNKNOWN";
    }
}

CYKTable::CYKTable(const std::vector<Token>& tokens, const grammar::Grammar& grammar) {
    n_ = tokens.size();
    if (n_ == 0 || n_ > MAX_TOKEN_LIMIT) {
        valid_ = false;
        return;
    }

    table_.resize(n_, std::vector<Cell>(n_));
    fill(tokens, grammar);
}

void CYKTable::fill(const std::vector<Token>& tokens, const grammar::Grammar& grammar) {
    // Row 1: Length = 1 (Unary Terminal Expansions: A -> a)
    for (size_t i = 0; i < n_; ++i) {
        std::string terminalName = tokenTypeToString(tokens[i].type);
        const auto& lhsList = grammar.getUnary(terminalName);

        for (const auto& lhs : lhsList) {
            RuleMatch match;
            match.isTerminal = true;
            table_[i][0].addSymbol(lhs, match);
        }
    }

    // Rows 2..n: Spans of length 2 up to n (Binary Rules: A -> BC)
    for (size_t len = 2; len <= n_; ++len) {
        for (size_t start = 0; start <= n_ - len; ++start) {
            for (size_t k = 1; k < len; ++k) {
                const auto& leftCell = table_[start][k - 1];
                const auto& rightCell = table_[start + k][len - k - 1];

                if (leftCell.empty() || rightCell.empty()) {
                    continue;
                }

                for (const auto& [leftSym, _lMatch] : leftCell.matches) {
                    for (const auto& [rightSym, _rMatch] : rightCell.matches) {
                        const auto& parents = grammar.getBinary(leftSym, rightSym);

                        for (const auto& parent : parents) {
                            RuleMatch match;
                            match.isTerminal = false;
                            match.split = k;
                            match.leftSymbol = leftSym;
                            match.rightSymbol = rightSym;
                            table_[start][len - 1].addSymbol(parent, match);
                        }
                    }
                }
            }
        }
    }
}

bool CYKTable::accepted(const std::string& startSymbol) const {
    if (!valid_ || n_ == 0 || table_.empty()) {
        return false;
    }
    return table_[0][n_ - 1].hasSymbol(startSymbol);
}

const Cell& CYKTable::cell(size_t start, size_t len) const {
    return table_[start][len - 1];
}

size_t CYKTable::tokenCount() const {
    return n_;
}

bool CYKTable::isValid() const {
    return valid_;
}

} // namespace worklet2::cyk
