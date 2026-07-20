package com.fincore.api.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends ApiException {

    public InsufficientBalanceException() {
        super("Insufficient balance to complete this transaction", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
