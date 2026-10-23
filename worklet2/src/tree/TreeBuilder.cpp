#include "worklet2/tree/TreeBuilder.hpp"

namespace worklet2::tree {

std::shared_ptr<ParseTreeNode> TreeBuilder::build(const cyk::CYKTable& table,
                                                  const std::vector<Token>& tokens,
                                                  const std::string& startSymbol) {
    if (!table.isValid() || tokens.empty()) {
        return nullptr;
    }
    return buildNode(table, tokens, startSymbol, 0, tokens.size());
}

std::shared_ptr<ParseTreeNode> TreeBuilder::buildNode(const cyk::CYKTable& table,
                                                      const std::vector<Token>& tokens,
                                                      const std::string& symbol,
                                                      size_t start,
                                                      size_t len) {
    if (len == 0 || start + len > tokens.size()) {
        return nullptr;
    }

    const auto& c = table.cell(start, len);
    const auto* match = c.getMatch(symbol);
    if (!match) {
        return nullptr;
    }

    if (match->isTerminal) {
        std::string lex = (start < tokens.size()) ? tokens[start].lexeme : "";
        auto node = std::make_shared<ParseTreeNode>(symbol, start, len, true, lex);
        return node;
    }

    // Binary rule decomposition: A -> BC with split point k
    size_t k = match->split;
    if (k == 0 || k >= len) {
        return nullptr;
    }

    auto leftChild = buildNode(table, tokens, match->leftSymbol, start, k);
    auto rightChild = buildNode(table, tokens, match->rightSymbol, start + k, len - k);

    if (!leftChild || !rightChild) {
        return nullptr;
    }

    auto node = std::make_shared<ParseTreeNode>(symbol, start, len, false);
    node->left = leftChild;
    node->right = rightChild;
    return node;
}

} // namespace worklet2::tree
