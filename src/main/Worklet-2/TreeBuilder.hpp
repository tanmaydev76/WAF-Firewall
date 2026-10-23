#pragma once
#include "worklet2/common/Token.hpp"
#include "worklet2/cyk/CYKTable.hpp"
#include "worklet2/tree/ParseTreeNode.hpp"
#include <memory>
#include <string>
#include <vector>

namespace worklet2::tree {

class TreeBuilder {
public:
    static std::shared_ptr<ParseTreeNode> build(const cyk::CYKTable& table,
                                                const std::vector<Token>& tokens,
                                                const std::string& startSymbol);

private:
    static std::shared_ptr<ParseTreeNode> buildNode(const cyk::CYKTable& table,
                                                    const std::vector<Token>& tokens,
                                                    const std::string& symbol,
                                                    size_t start,
                                                    size_t len);
};

} // namespace worklet2::tree
