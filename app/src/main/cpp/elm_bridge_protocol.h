/*
 * elm_bridge_protocol.h
 *
 * Narrow protocol translation for the Hyundai-style K-line frames used by
 * CASCADE's interface. This does not open Bluetooth itself; it is the
 * deterministic frame/ELM command conversion layer used by the Android bridge.
 */
#ifndef CASCADE_ELM_BRIDGE_PROTOCOL_H
#define CASCADE_ELM_BRIDGE_PROTOCOL_H

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace cascade_elm {

// Validate an additive 8-bit checksum over every byte except the final byte.
bool hasChecksum(const std::vector<uint8_t>& frame);

// Convert a Hyundai request frame (68 6A F1 01 PID CS) to an ELM command
// (for example "0100"). Returns false for unsupported frame layouts.
bool requestToCommand(const std::vector<uint8_t>& frame, std::string* command);

// Parse an ELM response containing optional whitespace/CR/LF and optional
// SEARCHING... text. Converts a positive Mode 01 response to a Hyundai reply
// frame (48 6B 12 41 PID DATA... CS). Returns false for errors/negative replies.
bool responseToFrame(const std::string& response, uint8_t requestedPid,
                     std::vector<uint8_t>* frame);

} // namespace cascade_elm
#endif
