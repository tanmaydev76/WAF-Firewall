#pragma once
#include "worklet2/common/Token.hpp"
#include "worklet2/common/ValidationResult.hpp"
#include "worklet2/pipeline/StructuralValidator.hpp"
#include <string>
#include <vector>
#include <functional>

namespace worklet2 {

class Worklet2Gateway {
public:
    using Worklet3Delegate = std::function<bool(const std::string& sessionId, const std::string& action)>;

    explicit Worklet2Gateway(const StructuralValidator& validator);

    void setWorklet3Callback(Worklet3Delegate callback) {
        worklet3Callback_ = callback;
    }

    ValidationResult validateAndProcess(const std::string& sessionId,
                                       const std::string& httpMethod,
                                       const std::vector<Token>& tokens);

private:
    const StructuralValidator& validator_;
    Worklet3Delegate worklet3Callback_;
};

} // namespace worklet2

