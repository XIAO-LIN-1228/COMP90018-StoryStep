package com.comp90018.storystep.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.guidance.CardinalDirection;

import org.junit.Test;

/** The compass throttle: one announcement per change, not one per reading. */
public class CardinalAnnouncerTest {

    private final CardinalAnnouncer announcer = new CardinalAnnouncer();

    @Test
    public void theFirstReadingIsAnnounced() {
        assertTrue(announcer.shouldAnnounce(CardinalDirection.NORTH));
    }

    @Test
    public void anUnchangedDirectionIsNotRepeated() {
        announcer.shouldAnnounce(CardinalDirection.NORTH);

        // A compass delivers many readings a second. Without this the screen reader would be
        // saying "north" continuously and nothing else on the screen could be heard.
        assertFalse(announcer.shouldAnnounce(CardinalDirection.NORTH));
        assertFalse(announcer.shouldAnnounce(CardinalDirection.NORTH));
    }

    @Test
    public void crossingIntoANewCardinalIsAnnouncedOnce() {
        announcer.shouldAnnounce(CardinalDirection.NORTH);

        assertTrue(announcer.shouldAnnounce(CardinalDirection.NORTH_EAST));
        assertFalse(announcer.shouldAnnounce(CardinalDirection.NORTH_EAST));
    }

    @Test
    public void losingTheDirectionIsAnnouncedOnceAndRegainingItIsAnnouncedAgain() {
        announcer.shouldAnnounce(CardinalDirection.EAST);

        assertTrue("the user should hear that guidance stopped", announcer.shouldAnnounce(null));
        assertFalse(announcer.shouldAnnounce(null));
        assertTrue("and hear it when guidance comes back",
                announcer.shouldAnnounce(CardinalDirection.EAST));
    }

    @Test
    public void resettingMakesTheNextReadingAnnounceAgain() {
        announcer.shouldAnnounce(CardinalDirection.SOUTH);
        announcer.reset();

        assertTrue(announcer.shouldAnnounce(CardinalDirection.SOUTH));
    }
}
