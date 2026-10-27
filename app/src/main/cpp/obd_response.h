#pragma once

#include <string>

namespace cascade {

// Normalizes ELM327 output into hexadecimal tokens, discarding prompts,
// command echoes, adapter chatter and non-hexadecimal text.
std::string normalize_obd_hex(const std::string& input);

// Returns true only for a positive Mode 01 PID 00 response (41 00 + four
// data bytes). This validates framing, not vehicle-specific PID semantics.
bool is_mode01_pid00_response(const std::string& input);

}  // namespace cascade
