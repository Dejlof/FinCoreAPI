package com.fincore.api.integration.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyTransactionData(
        String reference,
        String status,
        long amount,
        String currency,
        @JsonProperty("gateway_response") String gatewayResponse
) {
    public boolean isSuccessful() {
        return "success".equalsIgnoreCase(status);
    }
}
