package com.comp90018.storystep.domain.repository;

/**
 * Raised when history is asked to delete a session that has not finished.
 *
 * <p>A walk in progress, or one restored as paused, is not history. Removing it here would discard
 * a session the user is still on, and silently — so this is refused rather than accommodated. The
 * session-recovery screen's Discard is the deliberate way to throw away an unfinished walk.
 */
public final class ActiveSessionNotDeletableException extends RuntimeException {
    public ActiveSessionNotDeletableException(String sessionId) {
        super("Session is still in progress and cannot be deleted from history: " + sessionId);
    }
}
