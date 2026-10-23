#pragma once
#include "Grammar.hpp"
#include <string>

namespace worklet2::grammar {

class GrammarConfig {
public:
    static bool loadFromFile(const std::string& filePath, Grammar& outGrammar, std::string& outError);
    static void loadDefaultRules(Grammar& outGrammar);
};

} // namespace worklet2::grammar