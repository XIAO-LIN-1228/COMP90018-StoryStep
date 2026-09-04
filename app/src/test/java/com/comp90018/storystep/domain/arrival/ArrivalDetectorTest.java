package com.comp90018.storystep.domain.arrival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.arrival.ArrivalDetector.Decision;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;

import org.junit.Before;
import org.junit.Test;

/**
 * FX-ARRIVAL, and the cases around it that decide whether an arrival is credible.
 *
 * <p>Every assertion here is about refusing to claim an arrival that did not happen. A missed
 * trigger costs the user a tap; a false one writes a place into their history that they never
 * visited, and there is no undo for that on the screen.
 *
 * <p>Offsets are derived from a fixed metres-per-degree of latitude rather than from the
 * calculator under test, so the distances the thresholds are checked against are independent of
 * the code doing the checking.
 */
public class ArrivalDetectorTest {

    /** Metres per degree of latitude, near enough for fixtures at this scale. */
    private static final double METERS_PER_DEGREE_LATITUDE = 111_195.0;

    private static final GeoPoint TARGET_POSITION = new GeoPoint(-37.79810, 144.95910);

    private static final Attraction TARGET = new Attraction(
            "old-arts",
            "Old Arts Building",
            AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.",
            false,
            TARGET_POSITION);

    private static final Attraction OTHER_TARGET = new Attraction(
            "system-garden",
            "System Garden",
            AttractionCategory.PARK,
            "A small botanical garden.",
            false,
            new GeoPoint(-37.79720, 144.96050));

    private ArrivalDetector detector;

    @Before
    public void setUp() {
        detector = new ArrivalDetector(ArrivalPolicy.defaultPolicy());
        detector.setTarget(TARGET);
    }

    // --- FX-ARRIVAL ----------------------------------------------------------------------------

    @Test
    public void threeCloseFixesAcrossTenSecondsQualify() {
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 0L));
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 5_000L));
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 10_000L));
    }

    @Test
    public void anArrivalIsReportedOnceWhileTheUserStaysThere() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 10_000L));

        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 15_000L));
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 20_000L));
    }

    @Test
    public void noTargetMeansThereIsNothingToArriveAt() {
        detector.setTarget(null);

        assertEquals(Decision.NO_TARGET, offer(8.0, 5.0f, 0L));
        assertNull(detector.getTarget());
    }

    // --- boundaries ----------------------------------------------------------------------------

    /**
     * The combined limit is inclusive.
     *
     * <p>Checked with the error circle alone against a tightened policy rather than by placing a
     * fixture exactly 25 m away: a distance that has been through a great-circle calculation is
     * only equal to 25 m to within floating-point rounding, and a test balanced on that rounding
     * would be measuring arithmetic, not the rule.
     */
    @Test
    public void distancePlusAccuracyExactlyAtTheLimitIsInside() {
        ArrivalDetector tight = new ArrivalDetector(
                new ArrivalPolicy(25.0, 25.0, 3, 10_000L, 15_000L, 15_000L));
        tight.setTarget(TARGET);

        assertEquals(Decision.WAITING, tight.offer(sampleAt(0.0, 25.0f, 0L), 0L));
        assertEquals(Decision.WAITING, tight.offer(sampleAt(0.0, 25.0f, 5_000L), 5_000L));
        assertEquals(Decision.ARRIVED, tight.offer(sampleAt(0.0, 25.0f, 10_000L), 10_000L));
    }

    @Test
    public void justBeyondTheCombinedLimitIsOutside() {
        ArrivalDetector tight = new ArrivalDetector(
                new ArrivalPolicy(25.0, 26.0, 3, 10_000L, 15_000L, 15_000L));
        tight.setTarget(TARGET);

        assertEquals(Decision.OUTSIDE, tight.offer(sampleAt(0.0, 25.001f, 0L), 0L));
    }

    @Test
    public void aFixWellInsideTheCombinedLimitCounts() {
        assertEquals(Decision.WAITING, offer(24.0, 25.0f, 0L));
    }

    @Test
    public void aFixWellBeyondTheCombinedLimitDoesNot() {
        assertEquals(Decision.OUTSIDE, offer(26.0, 25.0f, 0L));
    }

    @Test
    public void accuracyExactlyAtTheLimitIsUsable() {
        assertEquals(Decision.WAITING, offer(5.0, 25.0f, 0L));
    }

    @Test
    public void accuracyJustBeyondTheLimitIsNotUsableEvenStandingOnTheSpot() {
        assertEquals(Decision.POOR_ACCURACY, offer(0.0, 25.1f, 0L));
    }

    @Test
    public void aDwellOneMillisecondShortDoesNotQualify() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 9_999L));
    }

    @Test
    public void twoFixesSpanningLongEnoughAreStillTooFew() {
        offer(8.0, 5.0f, 0L);

        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 14_000L));
    }

    // --- resets --------------------------------------------------------------------------------

    @Test
    public void oneLuckyFixAmongDistantOnesNeverQualifies() {
        for (int i = 0; i < 5; i++) {
            offer(400.0, 5.0f, i * 5_000L);
            assertEquals(Decision.WAITING, offer(8.0, 5.0f, i * 5_000L + 1_000L));
        }
    }

    @Test
    public void leavingTheAreaDiscardsTheStaySoFar() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        assertEquals(Decision.OUTSIDE, offer(400.0, 5.0f, 7_000L));

        // Back inside, but counting from zero: the two earlier fixes described a stay that ended.
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 9_000L));
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 14_000L));
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 19_000L));
    }

    @Test
    public void aPoorFixDiscardsTheStaySoFar() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        assertEquals(Decision.POOR_ACCURACY, offer(8.0, 60.0f, 7_000L));

        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 10_000L));
    }

    @Test
    public void aStaleFixDiscardsTheStaySoFar() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        // Delivered late: the fix is 16 s old by the time it is offered.
        assertEquals(Decision.STALE,
                detector.offer(sampleAt(8.0, 5.0f, 6_000L), 22_000L));

        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 23_000L));
    }

    @Test
    public void aGapLongerThanThePolicyStartsTheStayAgain() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        // 15.001 s of silence: the app was not watching, so it cannot claim the user stayed.
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 20_001L));
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 25_001L));
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 30_001L));
    }

    @Test
    public void aGapExactlyAtThePolicyKeepsTheStay() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 20_000L));
    }

    // --- duplicates ----------------------------------------------------------------------------

    @Test
    public void aRepeatedTimestampIsIgnoredRatherThanCounted() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        assertEquals(Decision.OUT_OF_ORDER, offer(8.0, 5.0f, 5_000L));
        assertEquals(Decision.OUT_OF_ORDER, offer(8.0, 5.0f, 5_000L));

        // The duplicates neither counted towards the three fixes nor destroyed the two real ones.
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 10_000L));
    }

    @Test
    public void anOutOfOrderFixDoesNotDiscardTheStay() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        assertEquals(Decision.OUT_OF_ORDER, offer(400.0, 60.0f, 2_000L));

        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 10_000L));
    }

    // --- target changes ------------------------------------------------------------------------

    @Test
    public void switchingTargetsDiscardsTheStayTowardsTheOldOne() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);

        detector.setTarget(OTHER_TARGET);

        // The same position is far from the new target, so nothing has been arrived at.
        assertEquals(Decision.OUTSIDE, offer(8.0, 5.0f, 10_000L));
    }

    @Test
    public void switchingBackAllowsTheArrivalToBeReportedAgain() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 10_000L));

        detector.setTarget(OTHER_TARGET);
        detector.setTarget(TARGET);

        offer(8.0, 5.0f, 15_000L);
        offer(8.0, 5.0f, 20_000L);
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 25_000L));
    }

    @Test
    public void resetClearsTheStayAndLetsTheTargetBeReportedAgain() {
        offer(8.0, 5.0f, 0L);
        offer(8.0, 5.0f, 5_000L);
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 10_000L));

        // A resumed session is a new collection span; the previous one's dwell does not carry over.
        detector.reset();

        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 15_000L));
        assertEquals(Decision.WAITING, offer(8.0, 5.0f, 20_000L));
        assertEquals(Decision.ARRIVED, offer(8.0, 5.0f, 25_000L));
    }

    @Test
    public void resetDoesNotForgetTheTarget() {
        detector.reset();

        assertEquals(TARGET, detector.getTarget());
    }

    // --- decision helper -----------------------------------------------------------------------

    @Test
    public void onlyArrivedCountsAsAnArrival() {
        assertTrue(Decision.ARRIVED.isArrival());
        assertFalse(Decision.WAITING.isArrival());
        assertFalse(Decision.OUTSIDE.isArrival());
        assertFalse(Decision.NO_TARGET.isArrival());
    }

    // --- fixtures ------------------------------------------------------------------------------

    /** Offers a fix {@code metresNorth} from the target, delivered the instant it was taken. */
    private Decision offer(double metresNorth, float accuracyMeters, long monotonicMillis) {
        return detector.offer(sampleAt(metresNorth, accuracyMeters, monotonicMillis),
                monotonicMillis);
    }

    private static LocationSample sampleAt(double metresNorth,
                                           float accuracyMeters,
                                           long monotonicMillis) {
        GeoPoint position = new GeoPoint(
                TARGET_POSITION.getLatitude() + metresNorth / METERS_PER_DEGREE_LATITUDE,
                TARGET_POSITION.getLongitude());
        return new LocationSample(position, accuracyMeters, 1_000L + monotonicMillis,
                monotonicMillis, LocationOrigin.DEVICE);
    }
}
