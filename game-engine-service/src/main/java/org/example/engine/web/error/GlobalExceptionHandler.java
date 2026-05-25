package org.example.engine.web.error;

import org.example.engine.exception.GameAlreadyExistsException;
import org.example.engine.exception.GameNotFoundException;
import org.example.engine.exception.InvalidMoveException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<HttpErrorResponse> handleNotFound(GameNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new HttpErrorResponse("GAME_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(GameAlreadyExistsException.class)
    public ResponseEntity<HttpErrorResponse> handleAlreadyExists(GameAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new HttpErrorResponse("GAME_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(InvalidMoveException.class)
    public ResponseEntity<HttpErrorResponse> handleInvalidMove(InvalidMoveException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new HttpErrorResponse("INVALID_MOVE", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HttpErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new HttpErrorResponse("VALIDATION_ERROR", msg));
    }
}
