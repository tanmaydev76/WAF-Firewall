#pragma once
#include <string>
#include <memory>
#include <vector>

namespace worklet2 {

struct ParseTreeNode {
    std::string symbol;          // Non-terminal or terminal symbol name (e.g., "S", "M", "L", "K", "Value", "B")
    std::string lexeme;          // Lexeme if terminal (e.g., "{", "action", "CART", "}")
    bool isTerminal{false};
    size_t start{0};             // Starting token index in the stream
    size_t len{0};               // Length of token span

    std::shared_ptr<ParseTreeNode> left{nullptr};
    std::shared_ptr<ParseTreeNode> right{nullptr};

    ParseTreeNode() = default;
    ParseTreeNode(std::string sym, size_t s, size_t l, bool term = false, std::string lex = "")
        : symbol(std::move(sym)), lexeme(std::move(lex)), isTerminal(term), start(s), len(l) {}
};

} // namespace worklet2
