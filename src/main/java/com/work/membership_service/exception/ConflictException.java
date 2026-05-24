package com.work.membership_service.exception;

import org.springframework.http.HttpStatus;

// 409 — request collided with current state (illegal transition, duplicate, version conflict)
public class ConflictException extends BaseException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, "CONFLICT", message, cause);
    }
}
