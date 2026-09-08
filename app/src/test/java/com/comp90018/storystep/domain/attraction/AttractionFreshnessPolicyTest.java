package com.comp90018.storystep.domain.attraction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Boundary tests against explicit instants rather than against elapsed real time. */
public class AttractionFreshnessPolicyTest {

    private static final long HOUR = 60L * 60L * 1000L;
    private static final long DAY = 24L * HOUR;
    private static final long NOW = 1_700_000_000_000L;

    private final AttractionFreshnessPolicy policy = AttractionFreshnessPolicy.defaultPolicy();

    @Test
    public void theDefaultRefreshesAfterADayAndExpiresAfterAMonth() {
        assertEquals(DAY, policy.getRefreshAfterMillis());
        assertEquals(30L * DAY, policy.getExpireAfterMillis());
    }

    @Test
    public void contentInsideTheRefreshIntervalIsFresh() {
        assertEquals(AttractionFreshness.FRESH, policy.classify(NOW - HOUR, NOW));
    }

    @Test
    public void theRefreshBoundaryIsInclusiveOfFreshness() {
        assertEquals(AttractionFreshness.FRESH, policy.classify(NOW - DAY, NOW));
        assertEquals(AttractionFreshness.STALE, policy.classify(NOW - DAY - 1L, NOW));
    }

    @Test
    public void staleContentIsStillPresentable() {
        AttractionFreshness freshness = policy.classify(NOW - 3L * DAY, NOW);

        assertEquals(AttractionFreshness.STALE, freshness);
        assertTrue("a three-day-old list of museums is still a list of museums",
                freshness.isPresentable());
    }

    @Test
    public void contentPastTheExpiryIsNotPresentable() {
        AttractionFreshness freshness = policy.classify(NOW - 31L * DAY, NOW);

        assertEquals(AttractionFreshness.EXPIRED, freshness);
        assertFalse(freshness.isPresentable());
    }

    @Test
    public void aFetchTimeInTheFutureIsTreatedAsFreshRatherThanImpossiblyOld() {
        // A device whose clock moved backwards after the write. Discarding good content over a
        // clock adjustment would be the worse answer.
        assertEquals(AttractionFreshness.FRESH, policy.classify(NOW + HOUR, NOW));
    }

    @Test
    public void refreshIsRequestedForAnythingThatIsNotFresh() {
        assertFalse(policy.shouldRefresh(NOW - HOUR, NOW));
        assertTrue(policy.shouldRefresh(NOW - 2L * DAY, NOW));
        assertTrue(policy.shouldRefresh(NOW - 40L * DAY, NOW));
    }

    @Test
    public void anExpiryBeforeTheRefreshIntervalIsRejectedAsIncoherent() {
        try {
            new AttractionFreshnessPolicy(DAY, HOUR);
            fail("expected rejection of an expiry that precedes the refresh interval");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
