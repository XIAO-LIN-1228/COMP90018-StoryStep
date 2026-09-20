package com.comp90018.storystep.domain.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** The D-06 retention window, at its boundaries. */
public class RetentionPolicyTest {

    private static final long THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    @Test
    public void theDefaultWindowIsThirtyDays() {
        assertEquals(THIRTY_DAYS_MILLIS, RetentionPolicy.defaultPolicy().getKeepForMillis());
    }

    @Test
    public void aRowFetchedExactlyAtTheWindowIsStillKept() {
        RetentionPolicy policy = RetentionPolicy.defaultPolicy();
        long now = 1_000_000_000_000L;
        long fetchedExactlyThirtyDaysAgo = now - THIRTY_DAYS_MILLIS;

        // Callers delete strictly below the cutoff, so equality means the row survives.
        assertEquals(fetchedExactlyThirtyDaysAgo, policy.cutoffFor(now));
        assertTrue("a row at exactly thirty days must not be deleted",
                fetchedExactlyThirtyDaysAgo >= policy.cutoffFor(now));
    }

    @Test
    public void aRowOneMillisecondOlderThanTheWindowIsRemoved() {
        RetentionPolicy policy = RetentionPolicy.defaultPolicy();
        long now = 1_000_000_000_000L;
        long fetched = now - THIRTY_DAYS_MILLIS - 1L;

        assertTrue(fetched < policy.cutoffFor(now));
    }

    @Test
    public void aClockBeforeTheWindowClampsToZeroRatherThanGoingNegative() {
        // A device whose clock sits early in the epoch would otherwise produce a negative cutoff,
        // and a negative bound against an unsigned-looking timestamp is how a sweep ends up
        // deleting nothing or everything depending on the comparison.
        assertEquals(0L, RetentionPolicy.defaultPolicy().cutoffFor(1_000L));
    }

    @Test
    public void aWindowMustBePositive() {
        try {
            new RetentionPolicy(0L);
            fail("a zero window would delete every cached row on every launch");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("keepForMillis"));
        }
    }
}
