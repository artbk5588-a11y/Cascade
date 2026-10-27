#include "obd_response.h"

#include <cctype>
#include <sstream>
#include <vector>

namespace cascade {

std::string normalize_obd_hex(const std::string& input) {
    std::ostringstream out;
    bool pending_space = false;
    for (unsigned char ch : input) {
        if (std::isxdigit(ch)) {
            if (pending_space && out.tellp() > 0) out << ' ';
            out << static_cast<char>(std::toupper(ch));
            pending_space = false;
        } else {
            pending_space = true;
        }
    }
    return out.str();
}

bool is_mode01_pid00_response(const std::string& input) {
    const std::string normalized = normalize_obd_hex(input);
    std::istringstream stream(normalized);
    std::vector<std::string> tokens;
    std::string token;
    while (stream >> token) tokens.push_back(token);

    // Accept the standard spaced form and compact byte strings.
    std::string compact;
    for (const auto& part : tokens) compact += part;
    if (compact.size() < 12) return false;

    // Search for 41 00 followed by four data bytes. We require the positive
    // response marker and PID, not just an arbitrary substring.
    for (size_t i = 0; i + 12 <= compact.size(); i += 2) {
        if (compact.compare(i, 4, "4100") == 0) {
            for (size_t j = i; j < i + 12; j += 2) {
                if (!std::isxdigit(static_cast<unsigned char>(compact[j])) ||
                    !std::isxdigit(static_cast<unsigned char>(compact[j + 1]))) {
                    return false;
                }
            }
            return true;
        }
    }
    return false;
}

}  // namespace cascade
