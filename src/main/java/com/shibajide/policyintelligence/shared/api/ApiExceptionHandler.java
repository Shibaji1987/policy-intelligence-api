package com.shibajide.policyintelligence.shared.api;

import com.shibajide.policyintelligence.document.application.DocumentNotFoundException;
import com.shibajide.policyintelligence.document.application.DuplicateDocumentVersionException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(DocumentNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DuplicateDocumentVersionException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateDocumentVersionException exception) {
        return response(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT_VERSION", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> "field." + fieldError.getField(),
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return response(status, code, message, Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, Map<String, String> details) {
        return ResponseEntity.status(status).body(new ApiError(
                code,
                message,
                OffsetDateTime.now(ZoneOffset.UTC),
                details
        ));
    }
}
