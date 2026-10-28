#include "iface_elm.h"
#include "cascade-core/serial.h"
#include "cascade-core/cpu.h"
#include "cascade-core/ui.h"
#include "elm_bridge_protocol.h"

IfaceElm::IfaceElm(Cpu* cpu, UI* ui)
    : Interface(ui), cpu_(cpu), deadline_(0), baudDivisor_(0), state_(IDLE) {
    ui->setPort("ELM327 SPP");
}

void IfaceElm::setBaudDivisor(int divisor) {
    std::lock_guard<std::mutex> lock(mutex_);
    baudDivisor_ = divisor;
    if (serial) serial->flushRxBuf();
    ui->setBaudrate(10400, cpu_->serDivToBaud(divisor));
}

void IfaceElm::sendByte(uint8_t byte) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ == IDLE && (baudDivisor_ == 0x77 || baudDivisor_ == 0x76 || baudDivisor_ == 0x81)) {
        request_.clear();
        request_.push_back(byte);
        state_ = COMPOSING;
        deadline_ = cpu_->getCycles() + 8ULL * baudDivisor_ * 10ULL * 2ULL;
    } else if (state_ == COMPOSING) {
        if (request_.size() < 128) request_.push_back(byte);
        deadline_ = cpu_->getCycles() + 8ULL * baudDivisor_ * 10ULL * 2ULL;
    }
}

void IfaceElm::checkInput() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ == COMPOSING && cpu_->getCycles() >= deadline_) {
        if (cascade_elm::requestToCommand(request_, &pendingCommand_)) {
            state_ = WAITING_RESPONSE;
        } else {
            // Unsupported native protocol frame: do not fabricate ECU data.
            state_ = IDLE;
            request_.clear();
        }
    }
    if (state_ == RESPONSE_READY) {
        if (serial && !responseFrame_.empty()) {
            std::vector<int> terminated = responseFrame_;
            terminated.push_back(-1);
            serial->addRxData(terminated.data());
        }
        responseFrame_.clear();
        request_.clear();
        pendingCommand_.clear();
        state_ = IDLE;
    }
}

std::string IfaceElm::pollCommand() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != WAITING_RESPONSE) return std::string();
    // Mark as claimed so Java polling cannot dispatch the same request twice.
    state_ = RESPONSE_READY;
    return pendingCommand_;
}

bool IfaceElm::submitResponse(const std::string& command, const std::string& response) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != RESPONSE_READY || command != pendingCommand_) return false;
    const uint8_t pid = request_.size() > 4 ? request_[4] : 0;
    if (!cascade_elm::responseToFrame(response, pid,
                                      reinterpret_cast<std::vector<uint8_t>*>(&responseFrame_))) {
        responseFrame_.clear();
        state_ = IDLE;
        pendingCommand_.clear();
        request_.clear();
        return false;
    }
    return true;
}

bool IfaceElm::hasPendingCommand() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return state_ == WAITING_RESPONSE;
}

void IfaceElm::sendSlowInit(uint8_t) {
    // ELM327 performs K-line initialization internally on the first OBD command.
    // This adapter intentionally does not fake a successful slow-init reply.
}

void IfaceElm::slowInitImminent() {
}
