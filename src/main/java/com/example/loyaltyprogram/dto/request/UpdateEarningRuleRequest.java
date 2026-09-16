package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.validation.Validate;

import java.time.LocalDateTime;

public record UpdateEarningRuleRequest(
        Long programId,
        int points,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public UpdateEarningRuleRequest {
        Validate.notNull(startDate, "startDate");
        Validate.date(startDate, endDate);
    }
}
