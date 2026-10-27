/*
 * Android headless UI shim for CASCADE core compilation.
 * Changes made 2026-10-28. Original CASCADE source remains under CasPL 1.0.
 * This shim deliberately provides no desktop rendering or ROM picker.
 */
#ifndef CASCADE_ANDROID_UI_SHIM_H
#define CASCADE_ANDROID_UI_SHIM_H

#include <cstdint>
#include "cpu.h"

class Serial;

enum {
  LED_SERIAL_ENABLE = 0, LED_SERIAL_RX, LED_SERIAL_TX, LED_SERIAL_BREAK,
  LED_EEPROM, LED_ECHO, LED_BEEP, LED_CAN, LED_IFACE, LED_REC, LED_PLAY,
  NUM_LEDS
};
enum {
  HINT_IGNITION = 0, HINT_VAG_BREAKDOWN, HINT_MITSUBISHI_DIAG, HINT_TEST,
  NUM_HINTS
};

class UI {
public:
  UI() = default;
  void setSerial(Serial*) {}
  void setBaudrate(int, int) {}
  void setCommLine(int) {}
  bool pollEvent(Event&) { return false; }
  void flip() {}
  void setDirty() {}
  void* getPixels() { return nullptr; }
  int screenX() { return 800; }
  int screenY() { return 480; }
  int screenStep() { return 800 * 4; }
  void setLED(int, bool) {}
  void setHint(int) {}
  void clearHint(int) {}
  void loadSaveState(statefile_t, bool) {}
  void quit() {}
  void updateTime(int) {}
  const char* getStateName(bool, const char* = "save", const char* = "sav") { return ""; }
  bool askUser(const char*, const char*, const char* = "OK", const char* = "Cancel") { return false; }
  void fatalError(const char*, const char* = nullptr, const char* = nullptr,
                  const char* = nullptr, const char* = nullptr) {}
  void showWarning(const char*, const char* = nullptr, const char* = nullptr,
                   const char* = nullptr) {}
  void loadRom() {}
  void machineStopped() {}
  void machineRunning() {}
  void writeText(int, int, const char*) {}
};

#endif
