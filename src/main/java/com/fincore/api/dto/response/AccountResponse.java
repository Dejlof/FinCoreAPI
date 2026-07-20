package com.fincore.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String accountType,
        String currency,
        BigDecimal balance,
        String status,
        Instant createdAt
) {}
