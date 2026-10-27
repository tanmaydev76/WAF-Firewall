package com.wafgateway;

import com.wafgateway.common.HttpRequest;
import com.wafgateway.pipeline.IntegratedPipeline;
import com.wafgateway.pipeline.IntegratedPipeline.PipelineDecision;

public class FullPipelineDemo {

    private static final String VALID_SID = "7f3a2b9c0d1e4f5a6b7c8d9e0f1a2b3c";

    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("   END-TO-END WAF PIPELINE INTEGRATION RUNNER                           ");
        System.out.println("   Worklet 1 (HTTP/Regex) -> Worklet 2 (CYK C++) -> Worklet 3 (TM Tape)");
        System.out.println("========================================================================\n");

        try {
            IntegratedPipeline pipeline = new IntegratedPipeline();

            // Scenario 1: User Login
            HttpRequest req1 = new HttpRequest.Builder()
                    .method("POST").path("/api/login")
                    .header("Content-Type", "application/json")
                    .cookie("WAF_SID", VALID_SID)
                    .body("{\"action\":\"LOGIN\"}")
                    .build();
            runCase(1, "Valid User Login (POST /api/login)", pipeline, req1);

            // Scenario 2: Add to Cart (requires active logged-in session)
            HttpRequest req2 = new HttpRequest.Builder()
                    .method("POST").path("/api/cart")
                    .header("Content-Type", "application/json")
                    .cookie("WAF_SID", VALID_SID)
                    .body("{\"action\":\"CART\"}")
                    .build();
            runCase(2, "Valid Cart Action (POST /api/cart)", pipeline, req2);

            // Scenario 3: SQL Injection Attack
            HttpRequest req3 = new HttpRequest.Builder()
                    .method("POST").path("/api/cart")
                    .header("Content-Type", "application/json")
                    .cookie("WAF_SID", VALID_SID)
                    .body("{\"item\":\"book%27%20OR%201%3D1--\"}")
                    .build();
            runCase(3, "SQL Injection Attack Payload", pipeline, req3);

            // Scenario 4: JSON Bomb Nesting Attack
            HttpRequest req4 = new HttpRequest.Builder()
                    .method("POST").path("/api/data")
                    .header("Content-Type", "application/json")
                    .cookie("WAF_SID", VALID_SID)
                    .body("{\"a\":{\"b\":{\"c\":{\"d\":\"bomb\"}}}}")
                    .build();
            runCase(4, "JSON Bomb Nesting Attack (Depth 4 > 3)", pipeline, req4);

            // Scenario 5: Out-of-order Transition for New Session (Checkout without Login)
            String newSid = "11112222333344445555666677778888";
            HttpRequest req5 = new HttpRequest.Builder()
                    .method("POST").path("/api/checkout")
                    .header("Content-Type", "application/json")
                    .cookie("WAF_SID", newSid)
                    .body("{\"action\":\"CHECKOUT\"}")
                    .build();
            runCase(5, "State Violation: Checkout without Login", pipeline, req5);

            System.out.println("========================================================================");
            System.out.println(" FULL PIPELINE DEMONSTRATION EXECUTED SUCCESSFULLY                      ");
            System.out.println("========================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runCase(int index, String title, IntegratedPipeline pipeline, HttpRequest req) {
        System.out.println("[" + index + "] " + title);
        PipelineDecision decision = pipeline.process(req);
        System.out.println("    Verdict : " + (decision.allowed() ? "ALLOWED [200 OK]" : "BLOCKED [403 FORBIDDEN]"));
        System.out.println("    Stage   : " + decision.stage());
        if (decision.action() != null) {
            System.out.println("    Action  : " + decision.action());
        }
        System.out.println("    Details : " + decision.details());
        System.out.println();
    }
}
