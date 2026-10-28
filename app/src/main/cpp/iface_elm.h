#ifndef CASCADE_IFACE_ELM_H
#define CASCADE_IFACE_ELM_H

#include "interface.h"
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

class Cpu;
class Serial;

// Adapter for the Hyundai-style K-line byte frames used by CASCADE. Java
// owns the Bluetooth socket; this class exposes one outstanding ELM request
// at a time across JNI.
class IfaceElm : public Interface {
public:
    IfaceElm(Cpu* cpu, UI* ui);
    void setBaudDivisor(int divisor) override;
    void checkInput() override;
    void sendByte(uint8_t byte) override;
    void sendSlowInit(uint8_t target) override;
    void slowInitImminent() override;

    std::string pollCommand();
    bool submitResponse(const std::string& command, const std::string& response);
    bool hasPendingCommand() const;

private:
    enum State { IDLE, COMPOSING, WAITING_RESPONSE, COMMAND_CLAIMED, RESPONSE_READY, SI_WAIT_55, SI_WAIT_KEYWORDS, SI_WAIT_CHECK, SI_CHECK_RECEIVED };
    Cpu* cpu_;
    uint64_t deadline_;
    int baudDivisor_;
    uint8_t slowInitTarget_;
    uint8_t slowInitCheckByte_;
    State state_;
    std::vector<uint8_t> request_;
    std::string pendingCommand_;
    std::vector<uint8_t> responseFrame_;
    mutable std::mutex mutex_;
};

#endif
