package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.validation.Validate;

public record UpdateUserRequest(
        String firstName,
        String lastName
) {
    public UpdateUserRequest {
        Validate.notNull(this, "request");
        Validate.notBlank(firstName, "firstName");
        Validate.notBlank(lastName, "lastName");
    }
}
