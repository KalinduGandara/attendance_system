package com.attendance.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://attendance.local/errors/";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Problem> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.status(), ex.code(), ex.getMessage(), null, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Problem.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Problem.FieldError(fe.getField(),
                        fe.getCode() == null ? "INVALID" : fe.getCode().toUpperCase(),
                        fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "validation", "Validation failed", errors, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Problem> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<Problem.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new Problem.FieldError(v.getPropertyPath().toString(), "INVALID", v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "validation", "Validation failed", errors, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Problem> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "forbidden", "Access denied", null, req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Problem> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "conflict",
                "Resource was modified by another request; reload and retry", null, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Problem> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "conflict", "Data integrity violation", null, req);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Problem> handleNotFound(NoHandlerFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "not-found", "Resource not found", null, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Unexpected server error", null, req);
    }

    private ResponseEntity<Problem> build(HttpStatus status, String code, String detail,
                                          List<Problem.FieldError> errors, HttpServletRequest req) {
        Problem body = new Problem(
                TYPE_BASE + code,
                status.getReasonPhrase(),
                status.value(),
                detail,
                req.getRequestURI(),
                req.getHeader("X-Request-Id"),
                errors
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
