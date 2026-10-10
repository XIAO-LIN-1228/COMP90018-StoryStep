package com.comp90018.storystep.ui.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.SunTimes;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stay time, the eight-hour limit and reuse of the fetched ensemble. */
public class AdviceViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    /** 23:00 UTC; the clock stands at 23:30. */
    private static final long FIRST_END_SECOND = 1_791_586_800L;
    private static final long NOW = FIRST_END_SECOND * 1000L + 30 * MINUTE;
    private static final GeoPoint ORIGIN = new GeoPoint(-37.7963, 144.9614);

    private static final class HeldRepository implements ForecastRepository {
        final List<RepositoryCallback<EnsemblePrecipitation>> waiting = new ArrayList<>();
        final List<RepositoryCallback<HourlyForecast>> hourlyWaiting = new ArrayList<>();

        @Override
        public void hourly(GeoPoint origin, RepositoryCallback<HourlyForecast> callback) {
            hourlyWaiting.add(callback);
        }

        @Override
        public void ensemble(GeoPoint origin, RepositoryCallback<EnsemblePrecipitation> callback) {
            waiting.add(callback);
        }
    }

    private final HeldRepository repository = new HeldRepository();
    private final FakeClock clock = FakeClock.startingAt(NOW);
    private final AdviceViewModel viewModel = new AdviceViewModel(repository, clock);

    /** 34 dry, daylit hours of one 40-member model, fetched now. */
    private static EnsemblePrecipitation dry() {
        long[] ends = new long[34];
        for (int h = 0; h < ends.length; h++) {
            ends[h] = FIRST_END_SECOND + h * 3_600L;
        }
        // Daylight across the whole span.
        ForecastPlace place = new ForecastPlace("UTC", 0, SunTimes.of(
                new long[] {ends[0] - 3_600L}, new long[] {ends[ends.length - 1]}));
        return new EnsemblePrecipitation(ends, Collections.singletonList(
                new EnsemblePrecipitation.Model("ICON", 1, new double[40][34])), place, NOW);
    }

    private AdviceUiState state() {
        return viewModel.getUiState().getValue();
    }

    @Test
    public void theTripIsWalkingPlusTheDefaultStayAtEveryStop() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);

        assertEquals(AdviceUiState.Status.LOADING, state().getStatus());
        assertEquals(40 * MINUTE + 3 * 5 * MINUTE, state().getTripMillis());
    }

    @Test
    public void theFetchedEnsembleBecomesAdvice() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onSuccess(dry());

        assertEquals(AdviceUiState.Status.CONTENT, state().getStatus());
        assertNotNull(state().getAdvice());
        assertEquals(25, state().getAdvice().getCandidates().size());
    }

    @Test
    public void changingTheStayRecomputesWithoutAskingAgain() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onSuccess(dry());

        viewModel.onStayChosen(30);

        assertEquals(1, repository.waiting.size());
        assertEquals(AdviceUiState.Status.CONTENT, state().getStatus());
        assertEquals(40 * MINUTE + 3 * 30 * MINUTE, state().getTripMillis());
    }

    @Test
    public void anEnsembleOlderThanThirtyMinutesIsAskedForAgain() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onSuccess(dry());
        clock.shiftWallClock(31 * MINUTE);

        viewModel.onStayChosen(15);

        assertEquals(2, repository.waiting.size());
        assertEquals(AdviceUiState.Status.LOADING, state().getStatus());
    }

    @Test
    public void overEightHoursIsTooLongAndAsksNothing() {
        // 8 stops x 60 min + 1 h walking = 9 h.
        viewModel.init(ORIGIN, HOUR, 8);
        repository.waiting.get(0).onSuccess(dry());

        viewModel.onStayChosen(60);

        assertEquals(AdviceUiState.Status.TOO_LONG, state().getStatus());
        assertEquals(1, repository.waiting.size());
    }

    @Test
    public void aShorterStayAfterTooLongUsesTheHeldEnsemble() {
        viewModel.init(ORIGIN, HOUR, 8);
        repository.waiting.get(0).onSuccess(dry());
        viewModel.onStayChosen(60);

        viewModel.onStayChosen(15);

        assertEquals(AdviceUiState.Status.CONTENT, state().getStatus());
        assertEquals(1, repository.waiting.size());
    }

    @Test
    public void aLateAnswerForAnOlderRequestIsIgnored() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        viewModel.onStayChosen(60);   // still within 8 h; asks again while the first is pending

        repository.waiting.get(0).onError(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.TIMEOUT));

        assertEquals(AdviceUiState.Status.LOADING, state().getStatus());
    }

    @Test
    public void failuresAndUnusableDataShowTheirReason() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onError(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.NO_NETWORK));

        assertEquals(ForecastUnavailableException.Reason.NO_NETWORK, state().getReason());

        viewModel.retry();
        // A model of 5 members cannot support advice.
        long[] ends = {FIRST_END_SECOND};
        repository.waiting.get(1).onSuccess(new EnsemblePrecipitation(ends,
                Collections.singletonList(new EnsemblePrecipitation.Model("tiny", 1,
                        new double[5][1])), ForecastPlace.unknown(), NOW));

        assertEquals(ForecastUnavailableException.Reason.UNUSABLE, state().getReason());
    }

    @Test
    public void modelNamesAreJoinedForTheSourceLine() {
        assertEquals("ICON", AdviceText.models(Collections.singletonList("ICON"), "and"));
        assertEquals("ICON and GFS", AdviceText.models(Arrays.asList("ICON", "GFS"), "and"));
        assertEquals("ICON, ECMWF and GFS",
                AdviceText.models(Arrays.asList("ICON", "ECMWF", "GFS"), "and"));
    }

    @Test
    public void theHourlyForecastIsAskedOnceAndJoinsTheAdviceWhenItArrives() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onSuccess(dry());
        HourlyForecast hourly = new HourlyForecast(java.util.Collections.singletonList(
                new HourlyForecast.Hour(FIRST_END_SECOND + 3_600L, 0,
                        com.comp90018.storystep.domain.forecast.SkyCondition.CLEAR)), NOW);

        repository.hourlyWaiting.get(0).onSuccess(hourly);
        viewModel.onStayChosen(15);

        assertEquals(1, repository.hourlyWaiting.size());
        assertEquals(hourly, state().getHourly());
        assertEquals(AdviceUiState.Status.CONTENT, state().getStatus());
    }

    @Test
    public void aFailedHourlyForecastLeavesTheAdviceAlone() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.hourlyWaiting.get(0).onError(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.TIMEOUT));
        repository.waiting.get(0).onSuccess(dry());

        assertEquals(AdviceUiState.Status.CONTENT, state().getStatus());
        assertEquals(null, state().getHourly());
    }

    @Test
    public void severeWeatherIsPendingUntilTheHourlyForecastAnswers() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onSuccess(dry());

        assertEquals(AdviceViewModel.HourlyState.PENDING, state().getHourlyState());

        repository.hourlyWaiting.get(0).onError(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.TIMEOUT));

        assertEquals(AdviceViewModel.HourlyState.FAILED, state().getHourlyState());
    }

    @Test
    public void retryAsksForTheHourlyForecastAgain() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.hourlyWaiting.get(0).onError(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.TIMEOUT));

        viewModel.retry();

        assertEquals(2, repository.hourlyWaiting.size());
    }

    @Test
    public void comingBackInALaterHourRecomputesNow() {
        viewModel.init(ORIGIN, 40 * MINUTE, 3);
        repository.waiting.get(0).onSuccess(dry());
        long firstNow = state().getAdvice().getCandidates().get(0).getStartMillis();

        clock.shiftWallClock(30 * MINUTE);   // 23:30 -> 00:00: a new hour, cache still fresh
        viewModel.onResume();

        assertEquals(1, repository.waiting.size());
        assertEquals(firstNow + 30 * MINUTE,
                state().getAdvice().getCandidates().get(0).getStartMillis());
    }
}
