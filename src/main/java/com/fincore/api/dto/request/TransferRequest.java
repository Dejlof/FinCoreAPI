package com.fincore.api.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Pattern(regexp = "^\\d{10}$", message = "Source account must be 10 digits")
        String sourceAccountNumber,
        @NotBlank @Pattern(regexp = "^\\d{10}$", message = "Destination account must be 10 digits")
        String destinationAccountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,
        @Size(max = 255) String narration
) {}
