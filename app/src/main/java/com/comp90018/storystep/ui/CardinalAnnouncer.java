package com.comp90018.storystep.ui;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.guidance.CardinalDirection;

/**
 * Decides when a change of direction is worth saying out loud.
 *
 * <p>A compass reading moves constantly. Marking the direction line as a live region would make a
 * screen reader narrate every tremor of the user's hand, which NFR-03.3 rules out: a continuous
 * stream of announcements is worse than none, because it buries everything else the screen says.
 *
 * <p>So the direction is announced only when the cardinal it rounds to actually changes — eight
 * possible announcements over a full turn instead of hundreds. Losing the direction entirely is
 * also a change worth one announcement, and regaining it after that is another.
 *
 * <p>Pure and stateful-but-frameworkless, so the throttle is testable without a device.
 */
public final class CardinalAnnouncer {

    @Nullable
    private CardinalDirection lastAnnounced;

    private boolean announcedUnavailable;

    /**
     * Whether {@code cardinal} should be announced now.
     *
     * <p>Null means the direction is not currently known. Calling this records the answer, so a
     * caller that asks must be prepared to announce.
     */
    public boolean shouldAnnounce(@Nullable CardinalDirection cardinal) {
        if (cardinal == null) {
            if (announcedUnavailable) {
                return false;
            }
            announcedUnavailable = true;
            lastAnnounced = null;
            return true;
        }
        if (cardinal == lastAnnounced) {
            return false;
        }
        lastAnnounced = cardinal;
        announcedUnavailable = false;
        return true;
    }

    /** Forgets what was last said, so the next reading is announced again. */
    public void reset() {
        lastAnnounced = null;
        announcedUnavailable = false;
    }
}
