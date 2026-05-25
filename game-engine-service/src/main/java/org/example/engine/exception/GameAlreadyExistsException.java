package org.example.engine.exception;

import java.util.UUID;

public class GameAlreadyExistsException extends RuntimeException {
    public GameAlreadyExistsException(UUID id) {
        super("Game already exists: " + id);
    }
}
