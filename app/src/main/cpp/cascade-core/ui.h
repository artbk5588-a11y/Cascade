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

enum UIKeyCode {
  UIKEY_m = 'm', UIKEY_t = 't', UIKEY_z = 'z', UIKEY_n = 'n',
  UIKEY_y = 'y', UIKEY_b = 'b', UIKEY_s = 's', UIKEY_l = 'l',
  UIKEY_r = 'r', UIKEY_f = 'f', UIKEY_e = 'e',
  UIKEY_0 = '0', UIKEY_1 = '1', UIKEY_2 = '2', UIKEY_3 = '3',
  UIKEY_4 = '4', UIKEY_5 = '5', UIKEY_6 = '6', UIKEY_7 = '7',
  UIKEY_8 = '8', UIKEY_9 = '9',
  UIKEY_LSHIFT = 1001, UIKEY_RSHIFT, UIKEY_KP_ENTER, UIKEY_RETURN,
  UIKEY_ESCAPE, UIKEY_UP, UIKEY_DOWN, UIKEY_LEFT, UIKEY_RIGHT,
  UIKEY_BACKSPACE, UIKEY_F1, UIKEY_F2, UIKEY_F3, UIKEY_F4, UIKEY_F5,
  UIKEY_F6, UIKEY_F7, UIKEY_F8, UIKEY_F9, UIKEY_F10, UIKEY_F12,
  UIKEY_KP0, UIKEY_KP1, UIKEY_KP2, UIKEY_KP3, UIKEY_KP4,
  UIKEY_KP5, UIKEY_KP6, UIKEY_KP7, UIKEY_KP8, UIKEY_KP9
};

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
  void setSerial(Serial*) {}\n  void setPort(const char*) {}
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
