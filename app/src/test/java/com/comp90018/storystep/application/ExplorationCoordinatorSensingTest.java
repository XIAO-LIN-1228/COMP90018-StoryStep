package com.comp90018.storystep.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.guidance.CardinalDirection;
import com.comp90018.storystep.domain.guidance.GuidanceSnapshot;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.MotionSample;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.sensor.FakeHeadingSource;
import com.comp90018.storystep.platform.sensor.FakeMotionSource;
import com.comp90018.storystep.platform.sensor.FixedDeclinationSource;
import com.comp90018.storystep.platform.sensor.HeadingAccuracy;

import org.junit.Before;
import org.junit.Test;

/**
 * The coordinator's ownership of the motion and heading sensors, and the honesty rules that decide
 * when a heading may be shown at all.
 *
 * <p>The bar throughout: a missing or untrustworthy sensor degrades what the screen offers and
 * never stops the session, and a magnetic heading is never passed off as a true one.
 */
public class ExplorationCoordinatorSensingTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private FakeClock clock;
    private FakeLocationSource locationSource;
    private FakeMotionSource motionSource;
    private FakeHeadingSource headingSource;
    private ExplorationCoordinator coordinator;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        locationSource = new FakeLocationSource();
        motionSource = new FakeMotionSource();
        headingSource = new FakeHeadingSource();
        coordinator = build(FixedDeclinationSource.melbourne());
    }

    // --- registration ---------------------------------------------------------------------------

    @Test
    public void startingASessionRegistersEachSensorExactlyOnce() {
        activeSession();

        assertEquals(1, motionSource.registrationCount());
        assertEquals(1, headingSource.registrationCount());
    }

    @Test
    public void nothingIsRegisteredBeforeTheUserStarts() {
        becomeUsable();

        assertFalse(motionSource.isRegistered());
        assertFalse(headingSource.isRegistered());
    }

    @Test
    public void pausingReleasesEverySensorImmediately() {
        activeSession();

        coordinator.onVisibilityChanged(false);

        assertFalse("a backgrounded session must not keep the accelerometer running",
                motionSource.isRegistered());
        assertFalse(headingSource.isRegistered());
    }

    @Test
    public void stoppingReleasesEverySensor() {
        activeSession();

        coordinator.requestStop();

        assertFalse(motionSource.isRegistered());
        assertFalse(headingSource.isRegistered());
    }

    @Test
    public void resumingRegistersOnceMoreRatherThanTwice() {
        activeSession();
        coordinator.onVisibilityChanged(false);
        coordinator.onVisibilityChanged(true);

        coordinator.requestResume();

        assertEquals(2, motionSource.registrationCount());
        assertTrue(motionSource.isRegistered());
    }

    @Test
    public void aReadingFromARetiredRegistrationIsIgnored() {
        activeSession();
        coordinator.requestStop();

        motionSource.emitToRetiredListener(motionSample(0.0f, 0.0f, 9.81f, 1_000L));

        assertEquals("a stopped session must not keep classifying",
                MovementState.UNKNOWN, coordinator.movementSnapshot().getState());
    }

    // --- degrading ------------------------------------------------------------------------------

    @Test
    public void aDeviceWithoutAnAccelerometerStillRecordsASession() {
        motionSource.setAvailable(false);

        activeSession();

        assertFalse(motionSource.isRegistered());
        assertTrue("location is exactly as useful without a motion sensor",
                locationSource.isRegistered());
        assertEquals(MovementState.UNAVAILABLE, coordinator.movementSnapshot().getState());
    }

    @Test
    public void aDeviceWithoutACompassStillRecordsASession() {
        headingSource.setAvailable(false);
        activeSession();

        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        coordinator.selectDestination(museum());

        GuidanceSnapshot guidance = coordinator.guidanceSnapshot();
        assertFalse("no compass means no arrow", guidance.hasRelativeBearing());
        assertTrue("but the bearing to the place is still known", guidance.hasBearing());
        assertEquals(CardinalDirection.SOUTH_EAST, guidance.getCardinal());
    }

    @Test
    public void withoutMotionSensingTheStateIsUnavailableRatherThanStationary() {
        motionSource.setAvailable(false);
        activeSession();

        assertEquals("no sensor is not the same claim as not moving",
                MovementState.UNAVAILABLE, coordinator.movementSnapshot().getState());
    }

    // --- heading honesty -------------------------------------------------------------------------

    @Test
    public void aHeadingIsCorrectedForDeclinationBeforeItIsUsed() {
        activeSession();
        coordinator.selectDestination(museum());
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        // Magnetic north with 11 degrees of easterly declination is true 11 degrees.
        headingSource.emit(0.0f);

        GuidanceSnapshot guidance = coordinator.guidanceSnapshot();
        double bearing = guidance.getTargetBearingDegrees();
        assertEquals("the relative turn must be measured from true north, not magnetic",
                bearing - 11.0, guidance.getRelativeBearingDegrees(), 0.5);
    }

    @Test
    public void aHeadingWithoutAFixIsNotTreatedAsTrueNorth() {
        activeSession();
        coordinator.selectDestination(museum());

        headingSource.emit(0.0f);

        assertFalse("without a position there is no declination, so there is no true heading",
                coordinator.guidanceSnapshot().hasRelativeBearing());
    }

    @Test
    public void anUnreliableHeadingIsNotPointedAtAnything() {
        activeSession();
        coordinator.selectDestination(museum());
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        headingSource.emit(0.0f);
        assertTrue(coordinator.guidanceSnapshot().hasRelativeBearing());

        headingSource.emit(90.0f, HeadingAccuracy.UNRELIABLE);

        assertFalse("an uncalibrated compass points somewhere plausible and wrong",
                coordinator.guidanceSnapshot().hasRelativeBearing());
        assertTrue("the bearing itself is unaffected by the compass",
                coordinator.guidanceSnapshot().hasBearing());
    }

    @Test
    public void aStaleHeadingStopsBeingUsed() {
        activeSession();
        coordinator.selectDestination(museum());
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        headingSource.emit(0.0f);

        clock.advance(30_000L);

        assertFalse("a heading from half a minute ago is where the phone used to point",
                coordinator.guidanceSnapshot().hasRelativeBearing());
    }

    @Test
    public void changingDestinationRepointsTheArrowWithoutANewFix() {
        activeSession();
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        headingSource.emit(0.0f);
        coordinator.selectDestination(museum());
        double towardsMuseum = coordinator.guidanceSnapshot().getRelativeBearingDegrees();

        coordinator.selectDestination(parkToTheWest());

        assertTrue("the arrow must follow the newly chosen place immediately",
                Math.abs(towardsMuseum - coordinator.guidanceSnapshot().getRelativeBearingDegrees())
                        > 10.0);
    }

    @Test
    public void resumingForgetsTheHeadingFromBeforeThePause() {
        activeSession();
        coordinator.selectDestination(museum());
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        headingSource.emit(0.0f);

        coordinator.onVisibilityChanged(false);
        coordinator.onVisibilityChanged(true);
        coordinator.requestResume();

        assertFalse("the phone may be pointing anywhere by the time the user comes back",
                coordinator.guidanceSnapshot().hasRelativeBearing());
    }

    @Test
    public void anObserverIsGivenTheCurrentSensingStateWhenItSubscribes() {
        activeSession();
        RecordingObserver observer = new RecordingObserver();

        coordinator.addObserver(observer);

        assertEquals(1, observer.movements);
        assertEquals(1, observer.guidances);
    }

    @Test
    public void discardingASessionClearsItsMovementTotals() {
        activeSession();
        coordinator.requestDiscard();

        assertEquals(0L, coordinator.movementSnapshot().getMovingMillis());
        assertNull(coordinator.guidanceSnapshot().getCardinal());
    }

    // --- helpers --------------------------------------------------------------------------------

    private ExplorationCoordinator build(FixedDeclinationSource declination) {
        return new ExplorationCoordinator(
                new SessionStateMachine(clock),
                new InMemorySessionStore(clock),
                locationSource,
                new LocationSampleFilter(LocationQualityPolicy.defaultPolicy()),
                clock,
                new SensingSources(motionSource, headingSource, declination));
    }

    private void becomeUsable() {
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    private void activeSession() {
        becomeUsable();
        coordinator.requestStart();
    }

    /** South-east of {@link #ORIGIN}. */
    private static Attraction museum() {
        return Attraction.seeded("melbourne-museum", "Melbourne Museum",
                AttractionCategory.MUSEUM, "A museum.", IndoorClassification.INDOOR,
                new GeoPoint(-37.80330, 144.97160));
    }

    private static Attraction parkToTheWest() {
        return Attraction.seeded("west-park", "A park to the west",
                AttractionCategory.PARK, "A park.", IndoorClassification.OUTDOOR,
                new GeoPoint(-37.79630, 144.94000));
    }

    private static LocationSample sampleAt(GeoPoint position,
                                           float accuracyMeters,
                                           long monotonicMillis) {
        return new LocationSample(position, accuracyMeters, 1_700_000_000_000L, monotonicMillis,
                LocationOrigin.DEVICE);
    }

    private static MotionSample motionSample(float x, float y, float z, long monotonicMillis) {
        return new MotionSample(x, y, z, monotonicMillis);
    }

    private static final class RecordingObserver implements ExplorationCoordinator.Observer {

        int movements;
        int guidances;

        @Override
        public void onSessionChanged(com.comp90018.storystep.domain.model.SessionSummary summary) {
        }

        @Override
        public void onSample(LocationSample sample) {
        }

        @Override
        public void onLocationUnavailable(
                com.comp90018.storystep.platform.location.LocationSource.Reason reason) {
        }

        @Override
        public void onMovementChanged(
                com.comp90018.storystep.domain.model.MovementSnapshot movement) {
            movements++;
        }

        @Override
        public void onGuidanceChanged(GuidanceSnapshot guidance) {
            guidances++;
        }
    }
}
