package io.github.forrestknight.buoy.api;

import io.github.forrestknight.buoy.service.DomainValidationException;
import io.github.forrestknight.buoy.service.DuplicateKeyException;
import io.github.forrestknight.buoy.service.NotFoundException;
import io.github.forrestknight.buoy.service.StaleVersionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps service-layer exceptions to RFC 9457 problem-details responses. Extends
 * {@link ResponseEntityExceptionHandler} so framework-raised errors (malformed
 * JSON, validation failures, unsupported media types, ...) speak the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "Duplicate key", e.getMessage());
    }

    @ExceptionHandler({StaleVersionException.class, ObjectOptimisticLockingFailureException.class})
    public ProblemDetail versionConflict(RuntimeException e) {
        String detail = e instanceof StaleVersionException
                ? e.getMessage()
                : "The resource was modified concurrently; re-fetch and retry";
        return problem(HttpStatus.CONFLICT, "Version conflict", detail);
    }

    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail domainValidation(DomainValidationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request fields are invalid");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
