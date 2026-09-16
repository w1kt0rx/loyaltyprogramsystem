package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.validation.Validate;

import java.time.LocalDateTime;

public record CreateRewardRequest(
        String name,
        int pointsCost,
        Integer availableQuantity,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public CreateRewardRequest {
        Validate.notBlank(name, "name");
        Validate.notNull(startDate, "startDate");
        Validate.positive(pointsCost, "pointsCost");
        Validate.nonNegative(availableQuantity, "availableQuantity");
        Validate.date(startDate, endDate);
    }
}
