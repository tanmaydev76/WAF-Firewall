#pragma once
#include <string>
#include <cstddef>

namespace worklet2 {

enum class TokenType {
    LBRACE,   // {
    RBRACE,   // }
    LBRACKET, // [
    RBRACKET, // ]
    COLON,    // :
    COMMA,    // ,
    STRING,   // "action", "checkout"
    NUMBER,   // numeric values
    TRUE_TOK, // true
    FALSE_TOK,// false
    NULL_TOK, // null
    UNKNOWN   // unmapped / invalid syntax
};

struct Token {
    TokenType type;
    std::string lexeme;
    size_t line{1};
    size_t column{1};
};

} // namespace worklet2
