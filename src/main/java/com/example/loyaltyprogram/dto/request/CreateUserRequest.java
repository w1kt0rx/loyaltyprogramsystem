package com.example.loyaltyprogram.dto.request;

import com.example.loyaltyprogram.validation.Validate;

public record CreateUserRequest(
        String email,
        String firstName,
        String lastName,
        Long programId
) {
    public CreateUserRequest {
        Validate.notNull(this, "request");
        Validate.email(email);
        Validate.notBlank(firstName, "firstName");
        Validate.notBlank(lastName, "lastName");
    }
}
