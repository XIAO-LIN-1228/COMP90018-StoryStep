package com.comp90018.storystep.domain.repository;

/**
 * Whatever a session owns outside the database - today, the photographs attached when it was
 * published. History deletion calls this so those go with the row; the platform supplies the
 * implementation, and the domain stays free of files.
 */
public interface SessionArtifacts {

    SessionArtifacts NONE = new SessionArtifacts() {
        @Override
        public void discard(String sessionId) {
        }

        @Override
        public void discardAll() {
        }
    };

    /** Removes everything the session owns. Called off the main thread; safe when nothing exists. */
    void discard(String sessionId);

    /** Removes everything every session owns. */
    void discardAll();
}
