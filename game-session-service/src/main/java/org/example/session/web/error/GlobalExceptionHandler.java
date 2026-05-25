package org.example.session.web.error;

import org.example.session.exception.EngineCommunicationException;
import org.example.session.exception.SessionAlreadyRunningException;
import org.example.session.exception.SessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<HttpErrorResponse> handleNotFound(SessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new HttpErrorResponse("SESSION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SessionAlreadyRunningException.class)
    public ResponseEntity<HttpErrorResponse> handleAlreadyRunning(SessionAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new HttpErrorResponse("SESSION_ALREADY_RUNNING", e.getMessage()));
    }

    @ExceptionHandler(EngineCommunicationException.class)
    public ResponseEntity<HttpErrorResponse> handleEngineCommunicationError(EngineCommunicationException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new HttpErrorResponse("ENGINE_COMMUNICATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<HttpErrorResponse> handleEngineError(RestClientResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new HttpErrorResponse("ENGINE_ERROR", e.getResponseBodyAsString()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<HttpErrorResponse> handleEngineCommunicationError(RestClientException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new HttpErrorResponse("ENGINE_COMMUNICATION_ERROR", e.getMessage()));
    }
}
