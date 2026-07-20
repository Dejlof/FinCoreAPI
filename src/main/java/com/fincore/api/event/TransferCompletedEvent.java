package com.fincore.api.event;

import java.math.BigDecimal;

public record TransferCompletedEvent(
        String reference,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency) {
}
