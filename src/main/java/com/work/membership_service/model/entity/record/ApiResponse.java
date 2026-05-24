package com.work.membership_service.model.entity.record;

import java.util.Map;

// the single envelope every endpoint returns
// status:  "SUCCESS" or "FAILURE"
// data:    set on success, null on failure
// error:   set on failure (via GlobalExceptionHandler), null on success
public record ApiResponse<T>(
        String status,
        T data,
        Error error
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("SUCCESS", data, null);
    }

    public static <T> ApiResponse<T> fail(Error error) {
        return new ApiResponse<>("FAILURE", null, error);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return fail(new Error(code, message, null));
    }

    public static <T> ApiResponse<T> fail(String code, String message, Map<String, String> fieldErrors) {
        return fail(new Error(code, message, fieldErrors));
    }

    // nested so the whole envelope lives in one file / one type
    public record Error(
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
    }
}
