#pragma once
#include "worklet2/common/Token.hpp"
#include "worklet2/common/ValidationResult.hpp"
#include "worklet2/pipeline/StructuralValidator.hpp"
#include "worklet2/grammar/Grammar.hpp"
#include <string>
#include <vector>
#include <functional>
#include <memory>

namespace worklet2 {

class Worklet2Gateway {
public:
    using Worklet3Delegate = std::function<bool(const std::string& sessionId, const std::string& action)>;

    Worklet2Gateway();
    explicit Worklet2Gateway(const StructuralValidator& validator);

    void setWorklet3Callback(Worklet3Delegate callback) {
        worklet3Callback_ = callback;
    }

    ValidationResult validateAndProcess(const std::string& sessionId,
                                       const std::string& httpMethod,
                                       const std::vector<Token>& tokens);

private:
    std::shared_ptr<grammar::Grammar> defaultGrammar_;
    std::shared_ptr<StructuralValidator> defaultValidator_;
    const StructuralValidator* validator_{nullptr};
    Worklet3Delegate worklet3Callback_;
};

} // namespace worklet2
