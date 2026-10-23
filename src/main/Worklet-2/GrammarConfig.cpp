#include "GrammarConfig.hpp"
#include <fstream>
#include <sstream>
#include <regex>

namespace worklet2::grammar {

void GrammarConfig::loadDefaultRules(Grammar& g) {
    g.clear();
    g.setStartSymbol("S");

    // Terminal Unary Rules (A -> a)
    g.addUnaryRule("B", "LBRACE");
    g.addUnaryRule("C", "RBRACE");
    g.addUnaryRule("D", "COLON");
    g.addUnaryRule("E", "COMMA");
    g.addUnaryRule("K", "STRING");
    g.addUnaryRule("Value", "STRING");

    // Binary Rules (A -> BC)
    g.addBinaryRule("S", "B", "M");
    g.addBinaryRule("Value", "B", "M");
    g.addBinaryRule("M", "L", "C");
    g.addBinaryRule("L", "K", "Y");
    g.addBinaryRule("P", "K", "Y");
    g.addBinaryRule("L", "P", "N");
    g.addBinaryRule("N", "E", "L");
    g.addBinaryRule("Y", "D", "Value");
}

bool GrammarConfig::loadFromFile(const std::string& filePath, Grammar& outGrammar, std::string& outError) {
    std::ifstream file(filePath);
    if (!file.is_open()) {
        outError = "Could not open config file: " + filePath;
        return false;
    }

    std::stringstream buffer;
    buffer << file.rdbuf();
    std::string content = buffer.str();

    outGrammar.clear();

    // 1. start_symbol
    std::regex startRegex(R"re("start_symbol"\s*:\s*"([^"]+)")re");
    std::smatch match;
    if (std::regex_search(content, match, startRegex)) {
        outGrammar.setStartSymbol(match[1].str());
    } else {
        outGrammar.setStartSymbol("S");
    }

    // 2. Unary rules: {"lhs": "...", "rhs": "..."}
    std::regex unaryRegex(R"re(\{\s*"lhs"\s*:\s*"([^"]+)"\s*,\s*"rhs"\s*:\s*"([^"]+)"\s*\})re");
    auto unaryBegin = std::sregex_iterator(content.begin(), content.end(), unaryRegex);
    auto unaryEnd = std::sregex_iterator();
    for (auto it = unaryBegin; it != unaryEnd; ++it) {
        outGrammar.addUnaryRule((*it)[1].str(), (*it)[2].str());
    }

    // 3. Binary rules: {"lhs": "...", "left": "...", "right": "..."}
    std::regex binaryRegex(R"re(\{\s*"lhs"\s*:\s*"([^"]+)"\s*,\s*"left"\s*:\s*"([^"]+)"\s*,\s*"right"\s*:\s*"([^"]+)"\s*\})re");
    auto binaryBegin = std::sregex_iterator(content.begin(), content.end(), binaryRegex);
    auto binaryEnd = std::sregex_iterator();
    for (auto it = binaryBegin; it != binaryEnd; ++it) {
        outGrammar.addBinaryRule((*it)[1].str(), (*it)[2].str(), (*it)[3].str());
    }

    return true;
}

} // namespace worklet2::grammar