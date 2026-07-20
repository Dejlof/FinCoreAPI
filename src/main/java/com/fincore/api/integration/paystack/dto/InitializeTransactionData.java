package com.fincore.api.integration.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InitializeTransactionData(
        @JsonProperty("authorization_url") String authorizationUrl,
        @JsonProperty("access_code") String accessCode,
        String reference
) {}
