package com.loanplatform.mambu.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class MambuMockExceptionHandler {

    @ExceptionHandler(MambuResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(MambuResourceNotFoundException ex) {
        log.warn("Mambu resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", ex.getMessage(), 404));
    }

    @ExceptionHandler(MambuInvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(MambuInvalidStateException ex) {
        log.warn("Mambu invalid state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("INVALID_STATE", ex.getMessage(), 409));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("INVALID_PARAMETERS", ex.getMessage(), 400));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("VALIDATION_ERROR", details, 400));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String reason = "Method " + ex.getMethod() + " not allowed";
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error("METHOD_NOT_ALLOWED", reason, 405));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Mambu mock unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "An unexpected error occurred in Mambu mock", 500));
    }

    private Map<String, Object> error(String errorCode, String message, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("errorCode", errorCode);
        body.put("errorReason", message);
        body.put("httpStatus", status);
        return body;
    }
}
