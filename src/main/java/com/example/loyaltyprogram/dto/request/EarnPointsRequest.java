package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.model.EarningEventType;
import com.example.loyaltyprogram.validation.Validate;

public record EarnPointsRequest(
        EarningEventType eventType,
        Long programId,
        Long earningRuleId,
        String referenceId
) {
    public EarnPointsRequest {
        Validate.notBlank(referenceId, "referenceId");
    }
}