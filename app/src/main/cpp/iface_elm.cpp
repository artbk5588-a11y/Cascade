#include "iface_elm.h"
#include "cascade-core/serial.h"
#include "cascade-core/cpu.h"
#include "cascade-core/ui.h"
#include "elm_bridge_protocol.h"

IfaceElm::IfaceElm(Cpu* cpu, UI* ui)
    : Interface(ui), cpu_(cpu), deadline_(0), baudDivisor_(0), slowInitTarget_(0), slowInitCheckByte_(0), state_(IDLE) {
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
    if (state_ == SI_WAIT_CHECK) {
        slowInitCheckByte_ = byte;
        state_ = SI_CHECK_RECEIVED;
        deadline_ = cpu_->getCycles() + 1000;
        return;
    }
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
    if (cpu_->getCycles() < deadline_) return;
    if (state_ == SI_WAIT_55) {
        if (serial) serial->addRxData(static_cast<uint8_t>(0x55));
        state_ = SI_WAIT_KEYWORDS;
        deadline_ = cpu_->getCycles() + 6ULL * 10000ULL;
        return;
    }
    if (state_ == SI_WAIT_KEYWORDS) {
        if (serial) {
            const int keywords[] = {0x08, 0x08, -1};
            serial->addRxData(keywords);
        }
        state_ = SI_WAIT_CHECK;
        return;
    }
    if (state_ == SI_CHECK_RECEIVED) {
        if (serial) serial->addRxData(static_cast<uint8_t>((~slowInitTarget_) & 0xff));
        state_ = IDLE;
        return;
    }
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
            std::vector<int> terminated;
            terminated.reserve(responseFrame_.size() + 1);
            for (uint8_t byte : responseFrame_) terminated.push_back(byte);
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
    state_ = COMMAND_CLAIMED;
    return pendingCommand_;
}

bool IfaceElm::submitResponse(const std::string& command, const std::string& response) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != COMMAND_CLAIMED || command != pendingCommand_) return false;
    const uint8_t pid = request_.size() > 4 ? request_[4] : 0;
    if (!cascade_elm::responseToFrame(response, pid, &responseFrame_)) {
        responseFrame_.clear();
        state_ = IDLE;
        pendingCommand_.clear();
        request_.clear();
        return false;
    }
    state_ = RESPONSE_READY;
    return true;
}

bool IfaceElm::hasPendingCommand() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return state_ == WAITING_RESPONSE;
}

void IfaceElm::sendSlowInit(uint8_t target) {
    // CASCADE expects the K-line slow-init handshake. ELM performs the physical
    // initialization itself on the first OBD request, while this compatibility
    // handshake keeps the emulated serial driver's state machine moving.
    std::lock_guard<std::mutex> lock(mutex_);
    slowInitTarget_ = target;
    state_ = SI_WAIT_55;
    deadline_ = cpu_->getCycles() + 300ULL * 10000ULL;
}

void IfaceElm::slowInitImminent() {
}
