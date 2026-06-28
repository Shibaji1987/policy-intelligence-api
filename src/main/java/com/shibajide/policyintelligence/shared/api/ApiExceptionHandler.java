package com.shibajide.policyintelligence.shared.api;

import com.shibajide.policyintelligence.document.application.DocumentNotFoundException;
import com.shibajide.policyintelligence.document.application.DuplicateDocumentVersionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

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

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                code,
                message,
                OffsetDateTime.now(ZoneOffset.UTC),
                Map.of()
        ));
    }
}
