package com.fincore.api.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super("%s not found with %s: %s".formatted(resource, field, value), HttpStatus.NOT_FOUND);
    }
}
