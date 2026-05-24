package com.work.membership_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// parent for every custom api exception
// holds the http status so the global handler can map it directly
@Getter
public abstract class BaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;

    protected BaseException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    protected BaseException(HttpStatus httpStatus, String code, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
