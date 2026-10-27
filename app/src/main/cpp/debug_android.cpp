#include <stdint.h>

// The upstream CASCADE sources expect these process-wide debug masks.
// Android builds keep diagnostics conservative by default.
uint32_t debug_level = 0;
uint32_t debug_level_unabridged = 0;
