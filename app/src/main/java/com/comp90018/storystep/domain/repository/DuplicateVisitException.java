package com.comp90018.storystep.domain.repository;

/**
 * Raised when a visit already exists for a session and attraction pair.
 *
 * <p>A distinct type rather than a generic storage failure because the two mean opposite things to
 * the caller: a failed write must be retried and must not be announced as a saved visit, while this
 * says the visit is already safely recorded and the only correct response is to stop trying.
 */
public class DuplicateVisitException extends RuntimeException {

    public DuplicateVisitException(String sessionId, String attractionId) {
        super("Visit already recorded for session " + sessionId + " and attraction " + attractionId);
    }

    public DuplicateVisitException(String sessionId, String attractionId, Throwable cause) {
        super("Visit already recorded for session " + sessionId + " and attraction " + attractionId,
                cause);
    }
}
