package com.work.membership_service.exception;

import org.springframework.http.HttpStatus;

// 400 — request was malformed or violated a domain rule
public class ValidationException extends BaseException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
