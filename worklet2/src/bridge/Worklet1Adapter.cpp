#include "worklet2/bridge/Worklet1Adapter.hpp"
#include <sstream>
#include <regex>
#include <cctype>

namespace worklet2::bridge {

static TokenType stringToTokenType(const std::string& typeStr) {
    if (typeStr == "LBRACE") return TokenType::LBRACE;
    if (typeStr == "RBRACE") return TokenType::RBRACE;
    if (typeStr == "LBRACKET") return TokenType::LBRACKET;
    if (typeStr == "RBRACKET") return TokenType::RBRACKET;
    if (typeStr == "COLON") return TokenType::COLON;
    if (typeStr == "COMMA") return TokenType::COMMA;
    if (typeStr == "STRING") return TokenType::STRING;
    if (typeStr == "NUMBER") return TokenType::NUMBER;
    if (typeStr == "TRUE") return TokenType::TRUE_TOK;
    if (typeStr == "FALSE") return TokenType::FALSE_TOK;
    if (typeStr == "NULL") return TokenType::NULL_TOK;
    return TokenType::UNKNOWN;
}

static std::string reasonToString(RejectReason reason) {
    switch (reason) {
        case RejectReason::NONE: return "NONE";
        case RejectReason::EMPTY_INPUT: return "EMPTY_INPUT";
        case RejectReason::TOKEN_LIMIT_EXCEEDED: return "TOKEN_LIMIT_EXCEEDED";
        case RejectReason::GRAMMAR_MISMATCH: return "GRAMMAR_MISMATCH";
        case RejectReason::TAG_MISMATCH: return "TAG_MISMATCH";
        case RejectReason::DEPTH_EXCEEDED: return "DEPTH_EXCEEDED";
        case RejectReason::DOWNSTREAM_REJECTED: return "DOWNSTREAM_REJECTED";
        default: return "UNKNOWN";
    }
}

std::vector<Token> Worklet1Adapter::parseTokensFromJson(const std::string& jsonTokens) {
    std::vector<Token> tokens;
    // Regex matching {"type":"...","value":"..."}
    std::regex tokenRegex(R"re(\{\s*"type"\s*:\s*"([^"]+)"\s*,\s*"value"\s*:\s*("(?:\\.|[^"\\])*"|[^,}]+))re");
    auto begin = std::sregex_iterator(jsonTokens.begin(), jsonTokens.end(), tokenRegex);
    auto end = std::sregex_iterator();

    for (auto it = begin; it != end; ++it) {
        std::string typeStr = (*it)[1].str();
        std::string valStr = (*it)[2].str();

        // Clean quotes from valStr
        if (valStr.size() >= 2 && valStr.front() == '"' && valStr.back() == '"') {
            valStr = valStr.substr(1, valStr.size() - 2);
        }
        size_t p;
        while ((p = valStr.find('\\')) != std::string::npos) {
            valStr.erase(p, 1);
        }
        while ((p = valStr.find('"')) != std::string::npos) {
            valStr.erase(p, 1);
        }

        tokens.push_back({stringToTokenType(typeStr), valStr, 1, 1});
    }

    if (tokens.empty() && !jsonTokens.empty()) {
        return tokenizeRawJson(jsonTokens);
    }

    return tokens;
}

std::vector<Token> Worklet1Adapter::tokenizeRawJson(const std::string& raw) {
    std::vector<Token> tokens;
    size_t i = 0;
    size_t line = 1;
    size_t col = 1;

    while (i < raw.size()) {
        char c = raw[i];

        if (std::isspace(static_cast<unsigned char>(c))) {
            if (c == '\n') { line++; col = 1; }
            else { col++; }
            i++;
            continue;
        }

        if (c == '{') {
            tokens.push_back({TokenType::LBRACE, "{", line, col++});
            i++;
        } else if (c == '}') {
            tokens.push_back({TokenType::RBRACE, "}", line, col++});
            i++;
        } else if (c == '[') {
            tokens.push_back({TokenType::LBRACKET, "[", line, col++});
            i++;
        } else if (c == ']') {
            tokens.push_back({TokenType::RBRACKET, "]", line, col++});
            i++;
        } else if (c == ':') {
            tokens.push_back({TokenType::COLON, ":", line, col++});
            i++;
        } else if (c == ',') {
            tokens.push_back({TokenType::COMMA, ",", line, col++});
            i++;
        } else if (c == '"') {
            size_t startCol = col;
            i++; col++;
            std::string s = "";
            while (i < raw.size() && raw[i] != '"') {
                if (raw[i] == '\\' && i + 1 < raw.size()) {
                    s += raw[i++]; col++;
                }
                s += raw[i++]; col++;
            }
            if (i < raw.size() && raw[i] == '"') {
                i++; col++;
            }
            tokens.push_back({TokenType::STRING, s, line, startCol});
        } else if (std::isdigit(static_cast<unsigned char>(c)) || c == '-') {
            size_t startCol = col;
            std::string num = "";
            while (i < raw.size() && (std::isdigit(static_cast<unsigned char>(raw[i])) || raw[i] == '.' || raw[i] == '-')) {
                num += raw[i++]; col++;
            }
            tokens.push_back({TokenType::NUMBER, num, line, startCol});
        } else if (std::isalpha(static_cast<unsigned char>(c))) {
            size_t startCol = col;
            std::string word = "";
            while (i < raw.size() && std::isalpha(static_cast<unsigned char>(raw[i]))) {
                word += raw[i++]; col++;
            }
            if (word == "true") tokens.push_back({TokenType::TRUE_TOK, "true", line, startCol});
            else if (word == "false") tokens.push_back({TokenType::FALSE_TOK, "false", line, startCol});
            else if (word == "null") tokens.push_back({TokenType::NULL_TOK, "null", line, startCol});
            else tokens.push_back({TokenType::UNKNOWN, word, line, startCol});
        } else {
            tokens.push_back({TokenType::UNKNOWN, std::string(1, c), line, col++});
            i++;
        }
    }
    return tokens;
}

std::string Worklet1Adapter::resultToJson(const ValidationResult& res) {
    std::ostringstream oss;
    oss << "{\n";
    oss << "  \"success\": " << (res.success ? "true" : "false") << ",\n";
    oss << "  \"sessionId\": \"" << res.sessionId << "\",\n";
    oss << "  \"action\": \"" << res.action << "\",\n";
    oss << "  \"reason\": \"" << reasonToString(res.reason) << "\",\n";
    oss << "  \"rejectReason\": \"" << res.rejectReason << "\"\n";
    oss << "}";
    return oss.str();
}

} // namespace worklet2::bridge
