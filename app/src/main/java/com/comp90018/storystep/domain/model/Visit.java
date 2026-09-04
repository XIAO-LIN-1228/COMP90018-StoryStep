package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * One recorded arrival at an attraction during a session.
 *
 * <p>Carries a snapshot of the name and story rather than only an identifier, for the same reason
 * a session snapshots its destination: the attraction cache is refreshed and evicted, and a visit
 * that could no longer say where the user had been would not be history.
 *
 * <p>Identity is the {@code (sessionId, attractionId)} pair, which storage enforces as unique.
 * That pair, not this object, is what makes a visit exactly-once.
 */
public final class Visit {

    private final String sessionId;
    private final String attractionId;
    private final long arrivalWallClockMillis;
    private final String attractionName;
    private final String story;
    private final boolean unread;

    public Visit(String sessionId,
                 String attractionId,
                 long arrivalWallClockMillis,
                 String attractionName,
                 String story,
                 boolean unread) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.attractionId = Objects.requireNonNull(attractionId, "attractionId");
        if (sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be empty");
        }
        if (attractionId.isEmpty()) {
            throw new IllegalArgumentException("attractionId must not be empty");
        }
        this.arrivalWallClockMillis = arrivalWallClockMillis;
        // Name and story stay nullable: an attraction the provider gave no description for is a
        // real case, and the screen says so rather than inventing something to display.
        this.attractionName = attractionName;
        this.story = story;
        this.unread = unread;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAttractionId() {
        return attractionId;
    }

    /** Epoch milliseconds of the arrival, for display; not for measuring intervals. */
    public long getArrivalWallClockMillis() {
        return arrivalWallClockMillis;
    }

    /** The attraction's name as it was when the visit happened, or null when unknown. */
    public String getAttractionName() {
        return attractionName;
    }

    /** The story text as it was when the visit happened, or null when there was none. */
    public String getStory() {
        return story;
    }

    /** True until the user has actually opened the revealed story. */
    public boolean isUnread() {
        return unread;
    }

    /** A copy marked read, used once the story has been presented. */
    public Visit asRead() {
        return unread
                ? new Visit(sessionId, attractionId, arrivalWallClockMillis, attractionName, story,
                        false)
                : this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Visit)) {
            return false;
        }
        Visit that = (Visit) other;
        return arrivalWallClockMillis == that.arrivalWallClockMillis
                && unread == that.unread
                && sessionId.equals(that.sessionId)
                && attractionId.equals(that.attractionId)
                && Objects.equals(attractionName, that.attractionName)
                && Objects.equals(story, that.story);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, attractionId, arrivalWallClockMillis, attractionName, story,
                unread);
    }

    @Override
    public String toString() {
        return "Visit{" + sessionId + ", " + attractionId + ", unread=" + unread + '}';
    }
}
