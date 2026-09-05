package com.comp90018.storystep.domain.repository;

/** Raised when the requested session is not in history, or is not finished yet. */
public final class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionId) {
        super("Session not found in history: " + sessionId);
    }
}
