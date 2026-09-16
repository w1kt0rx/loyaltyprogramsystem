package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.model.EarningEventType;
import com.example.loyaltyprogram.validation.Validate;

import java.time.LocalDateTime;

public record CreateEarningRuleRequest(
        EarningEventType eventType,
        int points,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public CreateEarningRuleRequest {
        Validate.notNull(eventType, "eventType");
        Validate.positive(points, "points");
        Validate.notNull(startDate, "startDate");
        Validate.date(startDate, endDate);
    }
}
