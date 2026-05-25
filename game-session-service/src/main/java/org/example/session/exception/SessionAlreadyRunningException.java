package org.example.session.exception;

import java.util.UUID;

public class SessionAlreadyRunningException extends RuntimeException {
    public SessionAlreadyRunningException(UUID id) {
        super("Session simulation is already running: " + id);
    }
}
