package com.fincore.api.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record DepositRequest(
        @NotBlank @Pattern(regexp = "^\\d{10}$") String accountNumber,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 255) String narration
) {}
