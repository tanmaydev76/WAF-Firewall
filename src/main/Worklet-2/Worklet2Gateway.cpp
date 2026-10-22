#include "worklet2/pipeline/Worklet2Gateway.hpp"

namespace worklet2 {

Worklet2Gateway::Worklet2Gateway(const StructuralValidator& validator)
    : validator_(validator) {}

ValidationResult Worklet2Gateway::validateAndProcess(const std::string& sessionId,
                                                   const std::string& httpMethod,
                                                   const std::vector<Token>& tokens) {
    // 1. Run the full CFG / CYK / Depth pipeline (sessionId remains isolated)
    ValidationResult result = validator_.validate(tokens);
    result.sessionId = sessionId;

    if (!result.success) {
        return result;
    }

    // 2. Append HTTP method to action (e.g. "POST" + "_" + "CART" -> "POST_CART")
    std::string baseAction = result.action.empty() ? "UNKNOWN_ACTION" : result.action;
    result.action = httpMethod.empty() ? baseAction : (httpMethod + "_" + baseAction);

    // 3. Forward (sessionId, action) to Java Worklet 3
    if (worklet3Callback_) {
        bool allowed = worklet3Callback_(result.sessionId, result.action);
        if (!allowed) {
            result.success = false;
            result.reason = RejectReason::DOWNSTREAM_REJECTED;
            result.rejectReason = "Rejected by Worklet 3 State Machine.";
            return result;
        }
    }

    return result;
}

} // namespace worklet2
