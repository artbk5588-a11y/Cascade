#include "elm_bridge_protocol.h"

#include <cctype>
#include <sstream>

namespace cascade_elm {
namespace {
int hexValue(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

bool parseHexBytes(const std::string& input, std::vector<uint8_t>* bytes) {
    if (!bytes) return false;
    bytes->clear();
    int high = -1;
    for (char c : input) {
        const int v = hexValue(c);
        if (v < 0) {
            if (std::isspace(static_cast<unsigned char>(c))) continue;
            // ELM may append prompt or textual SEARCHING; reject other text.
            return false;
        }
        if (high < 0) high = v;
        else {
            bytes->push_back(static_cast<uint8_t>((high << 4) | v));
            high = -1;
        }
    }
    return high < 0 && !bytes->empty();
}

void appendChecksum(std::vector<uint8_t>* frame) {
    unsigned int sum = 0;
    for (uint8_t b : *frame) sum += b;
    frame->push_back(static_cast<uint8_t>(sum & 0xff));
}
} // namespace

bool hasChecksum(const std::vector<uint8_t>& frame) {
    if (frame.size() < 2) return false;
    unsigned int sum = 0;
    for (size_t i = 0; i + 1 < frame.size(); ++i) sum += frame[i];
    return static_cast<uint8_t>(sum & 0xff) == frame.back();
}

bool requestToCommand(const std::vector<uint8_t>& frame, std::string* command) {
    if (!command || frame.size() != 6 ||
        frame[0] != 0x68 || frame[1] != 0x6a || frame[2] != 0xf1 ||
        frame[3] != 0x01 || !hasChecksum(frame)) {
        return false;
    }
    static const char hex[] = "0123456789ABCDEF";
    command->clear();
    command->push_back('0');
    command->push_back('1');
    command->push_back(hex[frame[4] >> 4]);
    command->push_back(hex[frame[4] & 0x0f]);
    return true;
}

bool responseToFrame(const std::string& response, uint8_t requestedPid,
                     std::vector<uint8_t>* frame) {
    if (!frame) return false;
    // Strip ELM's common non-data status line before parsing the actual payload.
    std::string cleaned;
    cleaned.reserve(response.size());
    for (char c : response) {
        if (c == '\r' || c == '\n' || c == '>' || std::isspace(static_cast<unsigned char>(c)))
            continue;
        cleaned.push_back(static_cast<char>(std::toupper(static_cast<unsigned char>(c))));
    }
    const std::string searching = "SEARCHING...";
    size_t searchingPos = 0;
    while ((searchingPos = cleaned.find(searching, searchingPos)) != std::string::npos) {
        cleaned.erase(searchingPos, searching.size());
    }
    std::vector<uint8_t> bytes;
    if (!parseHexBytes(cleaned, &bytes) || bytes.size() < 3 ||
        bytes[0] != 0x41 || bytes[1] != requestedPid) {
        return false;
    }
    frame->clear();
    frame->push_back(0x48);
    frame->push_back(0x6b);
    frame->push_back(0x12);
    frame->push_back(bytes[0]);
    frame->push_back(bytes[1]);
    frame->insert(frame->end(), bytes.begin() + 2, bytes.end());
    appendChecksum(frame);
    return true;
}
} // namespace cascade_elm
