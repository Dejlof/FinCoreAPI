package com.fincore.api.integration.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope used by every Paystack REST response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaystackApiResponse<T>(boolean status, String message, T data) {}
