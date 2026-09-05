package com.comp90018.storystep.domain.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;

import org.junit.Test;

/**
 * When a stored observation stops being current, and when it stops being worth showing.
 *
 * <p>Both boundaries are exercised with an injected instant rather than by waiting, which is the
 * only reason a thirty-minute and a six-hour rule are testable at all.
 */
public class WeatherFreshnessPolicyTest {

    private static final long FETCHED_AT = 1_700_000_000_000L;
    private static final long MINUTE = 60L * 1000L;

    private final WeatherFreshnessPolicy policy = WeatherFreshnessPolicy.defaultPolicy();

    private static WeatherSnapshot snapshotFetchedAt(long fetchedAtMillis) {
        return new WeatherSnapshot("0:0", WeatherCondition.CLEAR, 14.0,
                fetchedAtMillis, fetchedAtMillis, WeatherSnapshot.Provenance.NETWORK);
    }

    @Test
    public void aJustFetchedObservationIsFresh() {
        assertEquals(WeatherFreshness.FRESH,
                policy.classify(snapshotFetchedAt(FETCHED_AT), FETCHED_AT));
    }

    @Test
    public void exactlyAtTheRefreshIntervalIsStillFresh() {
        long now = FETCHED_AT + policy.getRefreshAfterMillis();

        assertEquals(WeatherFreshness.FRESH, policy.classify(snapshotFetchedAt(FETCHED_AT), now));
        assertFalse(policy.shouldRefresh(snapshotFetchedAt(FETCHED_AT), now));
    }

    @Test
    public void oneMillisecondPastTheRefreshIntervalIsStale() {
        long now = FETCHED_AT + policy.getRefreshAfterMillis() + 1L;

        assertEquals(WeatherFreshness.STALE, policy.classify(snapshotFetchedAt(FETCHED_AT), now));
        assertTrue(policy.shouldRefresh(snapshotFetchedAt(FETCHED_AT), now));
    }

    @Test
    public void staleIsStillWorthShowingButNotWorthRankingWith() {
        // The distinction the whole enum exists for: forty minutes ago is displayable with a
        // label, and is not a reason to reorder what is nearby now.
        assertTrue(WeatherFreshness.STALE.isPresentable());
        assertFalse(WeatherFreshness.STALE.isUsableAsContext());
        assertTrue(WeatherFreshness.FRESH.isUsableAsContext());
    }

    @Test
    public void exactlyAtTheExpiryIsStillStale() {
        long now = FETCHED_AT + policy.getExpireAfterMillis();

        assertEquals(WeatherFreshness.STALE, policy.classify(snapshotFetchedAt(FETCHED_AT), now));
    }

    @Test
    public void pastTheExpiryIsGone() {
        long now = FETCHED_AT + policy.getExpireAfterMillis() + 1L;

        assertEquals(WeatherFreshness.EXPIRED, policy.classify(snapshotFetchedAt(FETCHED_AT), now));
        assertFalse(WeatherFreshness.EXPIRED.isPresentable());
    }

    @Test
    public void noSnapshotIsExpired() {
        assertEquals(WeatherFreshness.EXPIRED, policy.classify(null, FETCHED_AT));
        assertTrue(policy.shouldRefresh(null, FETCHED_AT));
    }

    @Test
    public void aWallClockThatMovedBackwardsDoesNotDiscardGoodData() {
        // The device synced its time and jumped back an hour. The observation is minutes old in
        // reality; treating a negative age as impossibly stale would throw away a good answer.
        long now = FETCHED_AT - 60L * MINUTE;

        assertEquals(WeatherFreshness.FRESH, policy.classify(snapshotFetchedAt(FETCHED_AT), now));
    }

    @Test
    public void ageIsMeasuredFromTheFetchNotTheObservation() {
        // A provider serving an observation that was already an hour old when we fetched it: we
        // asked recently, so we do not ask again. Its age is still shown to the user from the
        // observation time carried on the snapshot.
        WeatherSnapshot latelyFetchedOldObservation = new WeatherSnapshot("0:0",
                WeatherCondition.RAIN, 9.0,
                FETCHED_AT - 60L * MINUTE, FETCHED_AT, WeatherSnapshot.Provenance.NETWORK);

        assertEquals(WeatherFreshness.FRESH,
                policy.classify(latelyFetchedOldObservation, FETCHED_AT + MINUTE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void expiryMayNotPrecedeRefresh() {
        new WeatherFreshnessPolicy(30L * MINUTE, 10L * MINUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNonPositiveRefreshIntervalIsRejected() {
        new WeatherFreshnessPolicy(0L, 10L * MINUTE);
    }
}
