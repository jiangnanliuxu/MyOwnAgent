package com.agentdesk.backend.common.error;

import com.agentdesk.backend.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage(), exception.getDetails()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldViolation)
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        ErrorCode.VALIDATION_FAILED.code(),
                        "Request validation failed.",
                        violations
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldViolation> violations = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        ErrorCode.VALIDATION_FAILED.code(),
                        "Request validation failed.",
                        violations
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable() {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(ErrorCode.BAD_REQUEST.code(), "Malformed JSON request."));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(
                        ErrorCode.NOT_FOUND.code(),
                        "No endpoint found for " + exception.getHttpMethod() + " " + exception.getRequestURL() + "."
                ));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleErrorResponse(ErrorResponseException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getBody().getDetail();
        if (message == null || message.isBlank()) {
            message = status.getReasonPhrase();
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.failure(status.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.code(), "Unexpected server error."));
    }

    private FieldViolation toFieldViolation(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        if (message == null || message.isBlank()) {
            message = "Invalid value.";
        }
        return new FieldViolation(fieldError.getField(), message);
    }
}
