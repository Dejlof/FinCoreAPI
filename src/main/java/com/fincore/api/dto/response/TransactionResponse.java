package com.fincore.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String reference,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        String type,
        String status,
        String narration,
        Instant createdAt,
        Instant completedAt
) {}
