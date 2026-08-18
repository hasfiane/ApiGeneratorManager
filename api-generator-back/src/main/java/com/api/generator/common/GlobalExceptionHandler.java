package com.api.generator.common;

import com.api.generator.runtime.error.NotFoundException;
import com.api.generator.config.TraceIdFilter;
import com.api.generator.account.service.PlanCapabilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@SuppressWarnings("unused")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Runtime errors ────────────────────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found: {} at {}", ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(404, "Not Found", ex.getMessage(), req));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResourceFound(NoResourceFoundException ex, HttpServletRequest req) {
        log.warn("Static resource not found: {} {}", req.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(404, "Not Found", ex.getMessage(), req));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error(409, "Conflict",
                        "Data integrity violation. Possible duplicate key or foreign key constraint.", req));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        log.warn("Constraint violation at {}: {} violations", req.getRequestURI(), ex.getConstraintViolations().size());
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(cv ->
                fieldErrors.put(cv.getPropertyPath().toString(), cv.getMessage())
        );
        return ResponseEntity.badRequest().body(error(
                400,
                "Bad Request",
                "Validation failed",
                req,
                Map.of("fields", fieldErrors)
        ));
    }

    // ── Back errors ───────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(error(400, "Bad Request", "Validation failed", req, Map.of("fields", fields)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? status.getReasonPhrase()
                : ex.getReason();
        return ResponseEntity.status(status)
                .body(error(status.value(), status.getReasonPhrase(), message, req));
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> handleAuth(Exception ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error(401, "Unauthorized", "Invalid credentials", req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(error(400, "Bad Request", ex.getMessage(), req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error(403, "Forbidden", "Access denied", req));
    }

    @ExceptionHandler(PlanCapabilityService.PlanLimitExceededException.class)
    public ResponseEntity<ApiError> handlePlanLimit(PlanCapabilityService.PlanLimitExceededException ex, HttpServletRequest req) {
        return ResponseEntity.status(422)
                .body(error(422, "Unprocessable Entity", ex.getMessage(), req));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurityBadRequest(SecurityException ex, HttpServletRequest req) {
        log.warn("Rejected request at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(error(400, "Bad Request", ex.getMessage(), req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(500, "Internal Server Error", "Unexpected error", req));
    }

    private ApiError error(int status, String error, String message, HttpServletRequest req) {
        return error(status, error, message, req, Map.of());
    }

    private ApiError error(int status, String error, String message, HttpServletRequest req, Map<String, Object> details) {
        Object traceId = req.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        return new ApiError(
                OffsetDateTime.now(),
                status,
                error,
                codeFrom(status, error),
                message,
                req.getRequestURI(),
                traceId == null ? null : String.valueOf(traceId),
                details
        );
    }

    private String codeFrom(int status, String error) {
        return (status + "_" + error).toUpperCase().replace(' ', '_');
    }
}
