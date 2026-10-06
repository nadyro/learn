package com.kestrel.commerce.shared.web;

import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 problem responses.
 *
 * <p>Spring's {@link ResponseEntityExceptionHandler} already handles framework exceptions (404 on unknown routes, 405,
 * 415, type mismatches, ...). We add our own error codes on top and make sure that:
 *
 * <ul>
 *   <li>expected business errors ({@link DomainException}) are logged at INFO, they are not incidents;
 *   <li>unexpected errors are logged at ERROR with the stack trace, and the client only gets a generic message with
 *       the request ID (never a stack trace or SQL error).
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomainException(DomainException ex, WebRequest request) {
        ErrorCode code = ex.errorCode();
        log.info("Request rejected with {}: {}", code, ex.getMessage());
        ProblemDetail problem = Problems.of(code, ex.getMessage());
        ex.properties().forEach(problem::setProperty);
        return toResponse(ex, problem, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLocking(OptimisticLockingFailureException ex, WebRequest request) {
        log.info("Optimistic locking conflict: {}", ex.getMessage());
        ProblemDetail problem = Problems.of(
                ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by another request. Reload it and try again.");
        return toResponse(ex, problem, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation", ex);
        ProblemDetail problem =
                Problems.of(ErrorCode.DATA_CONFLICT, "The request conflicts with the current state of the data.");
        return toResponse(ex, problem, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problem = Problems.of(ErrorCode.ACCESS_DENIED, "You are not allowed to perform this operation.");
        return toResponse(ex, problem, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception while processing {}", path(request), ex);
        ProblemDetail problem = Problems.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Contact support with the requestId if the problem persists.");
        return toResponse(ex, problem, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationFailed(ex, violations(ex.getBindingResult()), headers, request);
    }

    /**
     * Raised instead of {@link MethodArgumentNotValidException} when a controller method also has constraints on other
     * parameters (e.g. a validated header next to a {@code @Valid} body).
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                violations.addAll(violations(errors));
            } else {
                String parameter =
                        Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter");
                result.getResolvableErrors()
                        .forEach(error -> violations.add(new FieldViolation(parameter, message(error))));
            }
        }
        return validationFailed(ex, violations, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Do not echo the parser message: it can leak internal class names.
        ProblemDetail problem =
                Problems.of(ErrorCode.MALFORMED_REQUEST, "The request body is missing or is not valid JSON.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.enrich(problem, path(request));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** One invalid field. {@code field} is the JSON path, e.g. {@code shippingAddress.countryCode}. */
    public record FieldViolation(String field, String message) {}

    private static List<FieldViolation> violations(Errors errors) {
        List<FieldViolation> violations = new ArrayList<>();
        errors.getFieldErrors().forEach(error -> violations.add(new FieldViolation(error.getField(), message(error))));
        errors.getGlobalErrors()
                .forEach(error -> violations.add(new FieldViolation(error.getObjectName(), message(error))));
        return violations;
    }

    private ResponseEntity<Object> validationFailed(
            Exception ex, List<FieldViolation> violations, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem =
                Problems.of(ErrorCode.VALIDATION_FAILED, "The request contains invalid fields, see 'errors'.");
        violations.sort(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message));
        problem.setProperty("errors", violations);
        return handleExceptionInternal(ex, problem, headers, ErrorCode.VALIDATION_FAILED.status(), request);
    }

    private ResponseEntity<Object> toResponse(Exception ex, ProblemDetail problem, WebRequest request) {
        return handleExceptionInternal(
                ex, problem, new HttpHeaders(), HttpStatusCode.valueOf(problem.getStatus()), request);
    }

    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }
}
