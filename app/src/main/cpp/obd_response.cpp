#include "obd_response.h"

#include <cctype>
#include <sstream>
#include <vector>

namespace cascade {

static std::vector<std::string> hex_chunks(const std::string& input) {
    std::vector<std::string> chunks;
    std::string token;
    auto flush = [&]() {
        if (!token.empty()) {
            bool all_hex = true;
            for (unsigned char ch : token) {
                if (!std::isxdigit(ch)) { all_hex = false; break; }
            }
            if (all_hex) chunks.push_back(token);
            token.clear();
        }
    };

    // Keep alphanumeric words together so words like SEARCHING and ELM327
    // are rejected as whole tokens instead of leaking their A-F letters.
    for (unsigned char ch : input) {
        if (std::isalnum(ch)) token.push_back(static_cast<char>(ch));
        else flush();
    }
    flush();
    return chunks;
}

std::string normalize_obd_hex(const std::string& input) {
    const auto chunks = hex_chunks(input);
    std::ostringstream out;
    bool first = true;
    for (const auto& chunk : chunks) {
        if (!first) out << ' ';
        for (unsigned char ch : chunk) out << static_cast<char>(std::toupper(ch));
        first = false;
    }
    return out.str();
}

bool is_mode01_pid00_response(const std::string& input) {
    const auto chunks = hex_chunks(input);
    std::string compact;
    for (const auto& chunk : chunks) compact += chunk;

    // Search byte-aligned for the positive response marker and PID.
    for (size_t i = 0; i + 12 <= compact.size(); i += 2) {
        bool valid_bytes = true;
        for (size_t j = i; j < i + 12; j += 2) {
            if (!std::isxdigit(static_cast<unsigned char>(compact[j])) ||
                !std::isxdigit(static_cast<unsigned char>(compact[j + 1]))) {
                valid_bytes = false;
                break;
            }
        }
        if (valid_bytes && compact.compare(i, 4, "4100") == 0) return true;
    }
    return false;
}

}  // namespace cascade
