package com.comp90018.storystep.ui.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSegment;
import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionNotFoundException;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The detail screen's states.
 *
 * <p>NOT_FOUND is separate from ERROR on purpose, and both are separate from a blank screen: a
 * journey that was deleted, a read that failed and a screen that is still loading are three
 * different things to the user.
 */
public class JourneyDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final GeoPoint SOMEWHERE = new GeoPoint(-37.79630, 144.96140);

    @Test
    public void anUnknownSessionIsReportedAsNotFoundRatherThanAsAFailure() {
        FakeHistory history = new FakeHistory();
        history.failure = new SessionNotFoundException("gone");
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.load("gone");

        assertEquals(JourneyDetailUiState.Status.NOT_FOUND,
                viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void aStorageFailureIsReportedAsAnErrorRatherThanAsADeletedJourney() {
        FakeHistory history = new FakeHistory();
        history.failure = new IllegalStateException("database unavailable");
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.load("walk");

        assertEquals("a failed read might work next time; a deleted journey never will",
                JourneyDetailUiState.Status.ERROR,
                viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void navigatingHereWithNoSessionIdIsADeadEndRatherThanABlankScreen() {
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(new FakeHistory());

        viewModel.load(null);

        assertEquals(JourneyDetailUiState.Status.NOT_FOUND,
                viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void segmentsArePassedThroughWithoutBeingJoined() {
        FakeHistory history = new FakeHistory();
        history.detail = detail(
                Arrays.asList(
                        new RouteSegment(1, Arrays.asList(point(1), point(1))),
                        new RouteSegment(2, Collections.singletonList(point(2)))),
                MovementSnapshot.unavailable(),
                null,
                Collections.<Visit>emptyList());
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.load("walk");

        List<RouteSegment> segments =
                viewModel.getUiState().getValue().getDetail().getSegments();
        assertEquals("two recorded spans must stay two spans", 2, segments.size());
        assertEquals(2, segments.get(0).size());
        assertEquals(1, segments.get(1).size());
    }

    @Test
    public void aPhoneWithNoMotionSensorIsDistinguishableFromAMotionlessWalk() {
        FakeHistory noSensor = new FakeHistory();
        noSensor.detail = detail(Collections.<RouteSegment>emptyList(),
                MovementSnapshot.unavailable(), null, Collections.<Visit>emptyList());
        JourneyDetailViewModel unavailable = new JourneyDetailViewModel(noSensor);

        FakeHistory stoodStill = new FakeHistory();
        stoodStill.detail = detail(Collections.<RouteSegment>emptyList(),
                new MovementSnapshot(MovementState.STATIONARY, 600_000L, 0L), null,
                Collections.<Visit>emptyList());
        JourneyDetailViewModel motionless = new JourneyDetailViewModel(stoodStill);

        unavailable.load("walk");
        motionless.load("walk");

        assertEquals(MovementState.UNAVAILABLE,
                unavailable.getUiState().getValue().getDetail().getMovement().getState());
        assertEquals("standing still is a measurement, not a missing sensor",
                MovementState.STATIONARY,
                motionless.getUiState().getValue().getDetail().getMovement().getState());
    }

    @Test
    public void aJourneyWithNoRecordedWeatherReportsNone() {
        FakeHistory history = new FakeHistory();
        history.detail = detail(Collections.<RouteSegment>emptyList(),
                MovementSnapshot.unavailable(), null, Collections.<Visit>emptyList());
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.load("walk");

        assertFalse(viewModel.getUiState().getValue().getDetail().hasWeather());
    }

    @Test
    public void arecordedObservationReachesTheScreen() {
        FakeHistory history = new FakeHistory();
        history.detail = detail(Collections.<RouteSegment>emptyList(),
                MovementSnapshot.unavailable(),
                new WeatherSnapshot("-37.80,144.95", WeatherCondition.RAIN, 11.0,
                        1_700_000_000_000L, 1_700_000_001_000L,
                        WeatherSnapshot.Provenance.CACHE),
                Collections.<Visit>emptyList());
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.load("walk");

        assertTrue(viewModel.getUiState().getValue().getDetail().hasWeather());
        assertEquals(WeatherCondition.RAIN,
                viewModel.getUiState().getValue().getDetail().getWeather().getCondition());
    }

    @Test
    public void loadIfNeededDoesNotReReadTheSameJourney() {
        FakeHistory history = new FakeHistory();
        history.detail = detail(Collections.<RouteSegment>emptyList(),
                MovementSnapshot.unavailable(), null, Collections.<Visit>emptyList());
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.loadIfNeeded("walk");
        viewModel.loadIfNeeded("walk");

        assertEquals("a rotation must not re-read the database", 1, history.reads);
    }

    @Test
    public void loadIfNeededReadsAgainForADifferentJourney() {
        FakeHistory history = new FakeHistory();
        history.detail = detail(Collections.<RouteSegment>emptyList(),
                MovementSnapshot.unavailable(), null, Collections.<Visit>emptyList());
        JourneyDetailViewModel viewModel = new JourneyDetailViewModel(history);

        viewModel.loadIfNeeded("first");
        viewModel.loadIfNeeded("second");

        assertEquals("a second journey is not the one already on screen", 2, history.reads);
    }

    private static RoutePoint point(int segment) {
        return new RoutePoint(SOMEWHERE, 8.0f, 1_000L, segment);
    }

    private static SessionHistoryDetail detail(List<RouteSegment> segments,
                                               MovementSnapshot movement,
                                               WeatherSnapshot weather,
                                               List<Visit> visits) {
        return new SessionHistoryDetail(
                new SessionHistoryEntry("walk", 1_000L, 2_000L, 60_000L, 250.0,
                        visits.size(), segments.size(), false, "Museum"),
                visits, segments, movement, weather);
    }

    private static final class FakeHistory implements HistoryRepository {

        SessionHistoryDetail detail;
        Throwable failure;
        int reads;

        @Override
        public void loadHistory(RepositoryCallback<List<SessionHistoryEntry>> callback) {
            callback.onSuccess(Collections.<SessionHistoryEntry>emptyList());
        }

        @Override
        public void loadDetail(String sessionId,
                               RepositoryCallback<SessionHistoryDetail> callback) {
            reads++;
            if (failure != null) {
                callback.onError(failure);
                return;
            }
            callback.onSuccess(detail);
        }

        @Override
        public void delete(String sessionId, RepositoryCallback<Boolean> callback) {
            callback.onSuccess(false);
        }

        @Override
        public void clearAll(RepositoryCallback<Integer> callback) {
            callback.onSuccess(0);
        }

        @Override
        public void addObserver(Observer observer) {
        }

        @Override
        public void removeObserver(Observer observer) {
        }
    }
}
