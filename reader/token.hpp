
#ifndef _TOKEN_HPP
#define _TOKEN_HPP

#include <variant>
#include <string>
#include <optional>

enum class token_type {
    TOKEN_ID, TOKEN_NIL, TOKEN_COMPLEX, TOKEN_FPU,
    TOKEN_BIN, TOKEN_HEX, TOKEN_INT, TOKEN_STR,
    TOKEN_LBRA, TOKEN_RBRA, TOKEN_OVER, TOKEN_SLASH,
    TOKEN_ATOP, TOKEN_MAP, TOKEN_TACK, TOKEN_FORK,
    TOKEN_BIND, TOKEN_LPAR, TOKEN_RPAR, TOKEN_QUOT,
    TOKEN_EMPTY
};

class token {
    public:
        std::optional<std::variant<std::wstring, std::string>> content;
        token_type type;
        unsigned line, col, loc;

        token(unsigned loc, unsigned line, unsigned col, std::wstring content, token_type type)
            : content(content), type(type), line(line), col(col), loc(loc) { }
        
        token(unsigned loc, unsigned line, unsigned col, std::string content, token_type type)
            : content(content), type(type), line(line), col(col), loc(loc) { }
        
        token(unsigned loc, unsigned line, unsigned col, token_type type)
            : content(std::nullopt), type(type), line(line), col(col), loc(loc) { }
        
        token()
            : content(std::nullopt), type(token_type::TOKEN_EMPTY), line(-1), col(-1), loc(-1) { }
};

#endif
