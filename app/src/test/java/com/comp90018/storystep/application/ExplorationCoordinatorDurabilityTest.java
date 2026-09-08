package com.comp90018.storystep.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.DirectTransactions;
import com.comp90018.storystep.data.local.FakeRoutePointDao;
import com.comp90018.storystep.data.local.FakeVisitDao;
import com.comp90018.storystep.data.local.FakeSessionDao;
import com.comp90018.storystep.data.repository.RoomSessionStore;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;

import org.junit.Before;
import org.junit.Test;

/**
 * What survives the process, and what must not come back with it.
 *
 * <p>Process death is simulated by building a second coordinator over the same store, which is
 * exactly what a relaunch does: new objects, same database. The store is the durable one, driven
 * through fake DAOs on direct executors so the sequence is deterministic.
 */
public class ExplorationCoordinatorDurabilityTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private static final Attraction OLD_ARTS = Attraction.seeded(
            "old-arts", "Old Arts Building", AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.", IndoorClassification.OUTDOOR, new GeoPoint(-37.79810, 144.95910));

    private FakeClock clock;
    private FakeSessionDao sessionDao;
    private FakeRoutePointDao routePointDao;
    private FakeVisitDao visitDao;
    private SessionStore store;
    private FakeLocationSource locationSource;
    private ExplorationCoordinator coordinator;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        sessionDao = new FakeSessionDao();
        routePointDao = new FakeRoutePointDao();
        visitDao = new FakeVisitDao();
        store = new RoomSessionStore(new DirectTransactions(), sessionDao, routePointDao, visitDao,
                clock,
                Runnable::run, Runnable::run);
        locationSource = new FakeLocationSource();
        coordinator = newCoordinator();
    }

    // --- what gets written ---------------------------------------------------------------------

    @Test
    public void aStartedSessionIsWrittenBeforeAnythingIsCollected() {
        activeSession();

        assertNotNull(unfinished());
        assertEquals(coordinator.summary().getId(), unfinished().getSummary().getId());
    }

    @Test
    public void routePointsReachStorageAtTheCheckpointInterval() {
        activeSession();

        emit(ORIGIN, 1_000L);
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7963, 144.9615), 7_000L);

        assertEquals(2, routePointDao.countBySession(coordinator.summary().getId()));
    }

    @Test
    public void aCheckpointDoesNotRewriteThePointsItAlreadyStored() {
        activeSession();
        emit(ORIGIN, 1_000L);
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7963, 144.9615), 7_000L);
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7964, 144.9615), 13_000L);

        // Three fixes, three stored points — not three plus the two re-sent with them.
        assertEquals(3, routePointDao.totalRows());
    }

    @Test
    public void theChosenDestinationIsStoredWithTheSession() {
        activeSession();

        coordinator.selectDestination(OLD_ARTS);

        assertEquals("old-arts", unfinished().getDestination().getId());
    }

    @Test
    public void aDestinationChosenBeforeStartIsStoredOnceTheSessionExists() {
        coordinator.selectDestination(OLD_ARTS);

        activeSession();

        assertEquals("old-arts", unfinished().getDestination().getId());
    }

    // --- restoration -----------------------------------------------------------------------------

    @Test
    public void anInterruptedSessionComesBackPausedAndCollectsNothing() {
        activeSession();
        emit(ORIGIN, 1_000L);

        ExplorationCoordinator relaunched = relaunch();

        assertEquals(SessionState.PAUSED, relaunched.summary().getState());
        assertEquals(PauseReason.PROCESS_INTERRUPTED, relaunched.summary().getPauseReason());
        assertFalse("relaunching must not register a location source",
                locationSource.isRegistered());
    }

    @Test
    public void theRestoredSessionKeepsItsIdentityDurationAndDistance() {
        activeSession();
        emit(ORIGIN, 1_000L);
        clock.advance(6_000L);
        // Roughly 11.1 m north of the origin.
        emit(new GeoPoint(-37.79620, 144.96140), 7_000L);
        String sessionId = coordinator.summary().getId();
        double travelled = coordinator.routeSnapshot().getTotalDistanceMeters();

        ExplorationCoordinator relaunched = relaunch();

        assertEquals(sessionId, relaunched.summary().getId());
        assertEquals(6_000L, relaunched.summary().getActiveDurationMillis());
        assertEquals(travelled, relaunched.routeSnapshot().getTotalDistanceMeters(), 0.001);
        assertEquals(2, relaunched.routeSnapshot().getPoints().size());
    }

    @Test
    public void theRestoredSessionKeepsItsDestination() {
        activeSession();
        coordinator.selectDestination(OLD_ARTS);

        ExplorationCoordinator relaunched = relaunch();

        assertNotNull(relaunched.getDestination());
        assertEquals("old-arts", relaunched.getDestination().getId());
    }

    @Test
    public void aRestoredSessionHasNoCurrentFixUntilTheUserResumes() {
        activeSession();
        emit(ORIGIN, 1_000L);

        ExplorationCoordinator relaunched = relaunch();

        // A fix from before the interruption is not the user's position now.
        assertNull(relaunched.routeSnapshot().getLastSample());
    }

    @Test
    public void resumingARestoredSessionDoesNotBridgeTheGapWithDistance() {
        activeSession();
        emit(ORIGIN, 1_000L);
        double before = coordinator.routeSnapshot().getTotalDistanceMeters();
        ExplorationCoordinator relaunched = relaunch();

        usable(relaunched);
        relaunched.requestResume();
        // The first fix after the relaunch is about a kilometre from where the app left off.
        locationSource.emit(sample(new GeoPoint(-37.80530, 144.96140), 20_000L));

        assertEquals("travel nobody observed is not travel",
                before, relaunched.routeSnapshot().getTotalDistanceMeters(), 0.001);
    }

    @Test
    public void aFinishedSessionIsNotRestored() {
        activeSession();
        coordinator.requestStop();

        ExplorationCoordinator relaunched = relaunch();

        assertEquals(SessionState.IDLE, relaunched.summary().getState());
        assertNull(relaunched.summary().getId());
    }

    @Test
    public void aDiscardedSessionIsNotRestored() {
        activeSession();
        coordinator.requestDiscard();

        ExplorationCoordinator relaunched = relaunch();

        assertEquals(SessionState.IDLE, relaunched.summary().getState());
        assertEquals(0, sessionDao.size());
    }

    @Test
    public void continuingAfterARestoreAppendsWithoutDuplicatingTheStoredTrace() {
        activeSession();
        emit(ORIGIN, 1_000L);
        // Far enough past the fix for the checkpoint to be due, so the point is actually stored.
        clock.advance(6_000L);
        emit(new GeoPoint(-37.79625, 144.96140), 7_000L);
        assertEquals(2, routePointDao.countBySession(coordinator.summary().getId()));

        ExplorationCoordinator relaunched = relaunch();
        usable(relaunched);
        relaunched.requestResume();
        locationSource.emit(sample(new GeoPoint(-37.79620, 144.96140), 20_000L));
        clock.advance(6_000L);
        locationSource.emit(sample(new GeoPoint(-37.79610, 144.96140), 26_000L));

        // Two stored points from before the interruption plus the two recorded after it, with
        // nothing rewritten: the restored points keep the sequence numbers they already had.
        assertEquals(4, routePointDao.countBySession(relaunched.summary().getId()));
    }

    // --- failure ---------------------------------------------------------------------------------

    @Test
    public void aFailedCheckpointIsReportedWithoutStoppingTheSession() {
        activeSession();
        RecordingObserver observer = new RecordingObserver();
        coordinator.addObserver(observer);
        sessionDao.failWrites = new IllegalStateException("disk full");

        emit(ORIGIN, 1_000L);
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7963, 144.9615), 7_000L);

        assertTrue(coordinator.hasCheckpointFailed());
        assertTrue(observer.durability);
        assertEquals("collection is unaffected by a storage problem",
                SessionState.ACTIVE, coordinator.summary().getState());
        assertTrue(locationSource.isRegistered());
    }

    @Test
    public void theWarningClearsOnceACheckpointSucceedsAgain() {
        activeSession();
        sessionDao.failWrites = new IllegalStateException("disk full");
        emit(ORIGIN, 1_000L);
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7963, 144.9615), 7_000L);
        assertTrue(coordinator.hasCheckpointFailed());

        sessionDao.failWrites = null;
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7964, 144.9615), 13_000L);

        assertFalse(coordinator.hasCheckpointFailed());
    }

    @Test
    public void pointsMissedByAFailedCheckpointAreWrittenByTheNextOne() {
        activeSession();
        sessionDao.failWrites = new IllegalStateException("disk full");
        emit(ORIGIN, 1_000L);
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7963, 144.9615), 7_000L);

        sessionDao.failWrites = null;
        clock.advance(6_000L);
        emit(new GeoPoint(-37.7964, 144.9615), 13_000L);

        // Nothing was assumed written while the store was failing, so all three points land.
        assertEquals(3, routePointDao.countBySession(coordinator.summary().getId()));
    }

    // --- helpers -----------------------------------------------------------------------------

    private ExplorationCoordinator newCoordinator() {
        return new ExplorationCoordinator(
                new SessionStateMachine(clock),
                store,
                locationSource,
                new LocationSampleFilter(LocationQualityPolicy.defaultPolicy()),
                clock);
    }

    /** A fresh coordinator over the same store: new objects, same stored data. */
    private ExplorationCoordinator relaunch() {
        locationSource.stopUpdates();
        ExplorationCoordinator relaunched = newCoordinator();
        relaunched.restoreInterruptedSession();
        return relaunched;
    }

    private void activeSession() {
        usable(coordinator);
        coordinator.requestStart();
    }

    private static void usable(ExplorationCoordinator target) {
        target.onVisibilityChanged(true);
        target.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    private void emit(GeoPoint position, long monotonicMillis) {
        locationSource.emit(sample(position, monotonicMillis));
    }

    private static LocationSample sample(GeoPoint position, long monotonicMillis) {
        return new LocationSample(position, 5.0f, 1_700_000_000_000L + monotonicMillis,
                monotonicMillis, LocationOrigin.DEVICE);
    }

    private RestoredSession unfinished() {
        final RestoredSession[] found = new RestoredSession[1];
        store.findUnfinished(new RepositoryCallback<RestoredSession>() {
            @Override
            public void onSuccess(RestoredSession result) {
                found[0] = result;
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
        return found[0];
    }

    /** Records only the durability signal; the rest is covered by the main coordinator test. */
    private static final class RecordingObserver implements ExplorationCoordinator.Observer {

        boolean durability;

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
        public void onDurabilityChanged(boolean checkpointFailed) {
            durability = checkpointFailed;
        }
    }
}
