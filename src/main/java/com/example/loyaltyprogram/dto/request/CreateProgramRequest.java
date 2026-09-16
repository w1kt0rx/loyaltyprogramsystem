package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.validation.Validate;

import java.time.LocalDateTime;

public record CreateProgramRequest(
        String name,
        String description,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public CreateProgramRequest {
        Validate.notBlank(name, "name");
        Validate.notNull(startDate, "startDate");
        Validate.date(startDate, endDate);
    }
}
