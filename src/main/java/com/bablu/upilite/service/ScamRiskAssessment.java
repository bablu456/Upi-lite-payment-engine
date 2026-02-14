package com.bablu.upilite.service;

import java.util.ArrayList;
import java.util.List;

public record ScamRiskAssessment(ScamRiskAction action, int score, List<String> reasons) {

    public ScamRiskAssessment {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static ScamRiskAssessment allow() {
        return new ScamRiskAssessment(ScamRiskAction.ALLOW, 0, List.of());
    }

    public static ScamRiskAssessment challenge(int score, List<String> reasons) {
        return new ScamRiskAssessment(ScamRiskAction.CHALLENGE, score, sanitizeReasons(reasons));
    }

    public static ScamRiskAssessment block(int score, List<String> reasons) {
        return new ScamRiskAssessment(ScamRiskAction.BLOCK, score, sanitizeReasons(reasons));
    }

    private static List<String> sanitizeReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return List.of("Potentially risky payment pattern detected.");
        }

        List<String> safeReasons = new ArrayList<>();
        for (String reason : reasons) {
            if (reason == null) {
                continue;
            }
            String trimmed = reason.trim();
            if (!trimmed.isEmpty()) {
                safeReasons.add(trimmed);
            }
        }
        return safeReasons.isEmpty()
                ? List.of("Potentially risky payment pattern detected.")
                : safeReasons;
    }
}
