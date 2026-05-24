package com.work.membership_service.exception;

import com.work.membership_service.model.entity.record.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// maps every exception to the unified ApiResponse envelope with the right http status
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // any of our typed exceptions — uses their own httpStatus + code
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBase(BaseException ex) {
        log.warn("[err] base ex class: {}, code: {}, message: {}",
                ex.getClass().getSimpleName(), ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    // @Valid body failures — gather all field errors into the response
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fields.put(fe.getField(), fe.getDefaultMessage()));
        log.warn("[err] bean validation failed fields: {}", fields);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail("VALIDATION_FAILED", "request body invalid", fields));
    }

    // @Validated path/param failures
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        log.warn("[err] constraint violation: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail("VALIDATION_FAILED", ex.getMessage()));
    }

    // malformed json body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("[err] malformed request body: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail("BAD_JSON", "request body is not valid json"));
    }

    // raw IllegalArgumentException — typically from engines that did not throw a typed exception
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("[err] illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail("VALIDATION_FAILED", ex.getMessage()));
    }

    // db uniqueness or fk failures
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("[err] data integrity violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("CONFLICT", "operation violates a data constraint"));
    }

    // jpa optimistic lock — caller didnt catch it as ConflictException
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("[err] optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("CONFLICT", "resource was modified concurrently, retry"));
    }

    // last-resort
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex) {
        log.error("[err] unhandled ex class: {}, message: {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", "internal server error"));
    }
}
