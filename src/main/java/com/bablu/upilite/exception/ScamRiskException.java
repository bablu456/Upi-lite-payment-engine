package com.bablu.upilite.exception;

import com.bablu.upilite.service.ScamRiskAction;
import lombok.Getter;

import java.util.List;

@Getter
public class ScamRiskException extends RuntimeException {

    private final ScamRiskAction action;
    private final int riskScore;
    private final List<String> reasons;

    public ScamRiskException(String message, ScamRiskAction action, int riskScore, List<String> reasons) {
        super(message);
        this.action = action;
        this.riskScore = riskScore;
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
