package com.work.membership_service.exception;

import org.springframework.http.HttpStatus;

// 404 — the requested resource does not exist
public class NotFoundException extends BaseException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
