package com.fincore.api.integration.paystack;

import com.fincore.api.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PaystackException extends ApiException {

    public PaystackException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
