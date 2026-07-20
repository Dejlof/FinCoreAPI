package com.fincore.api.dto.request;

import com.fincore.api.entity.enums.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @NotNull AccountType accountType,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency
) {}
