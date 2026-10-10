package com.comp90018.storystep.ui.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The forecast page's states, with a repository that answers when told to. */
public class ForecastViewModelTest {

    @Rule
    public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    /** 23:00 UTC. */
    private static final long FIRST_END_SECOND = 1_791_586_800L;
    private static final GeoPoint ORIGIN = new GeoPoint(-37.8136, 144.9631);

    private static final class HeldRepository implements ForecastRepository {
        final List<RepositoryCallback<HourlyForecast>> waiting = new ArrayList<>();
        final List<GeoPoint> asked = new ArrayList<>();

        @Override
        public void hourly(GeoPoint origin, RepositoryCallback<HourlyForecast> callback) {
            asked.add(origin);
            waiting.add(callback);
        }

        @Override
        public void ensemble(GeoPoint origin, RepositoryCallback<EnsemblePrecipitation> callback) {
            throw new UnsupportedOperationException();
        }
    }

    private final HeldRepository repository = new HeldRepository();
    // 23:30 UTC: the first row (22:00-23:00) is over.
    private final FakeClock clock = FakeClock.startingAt(FIRST_END_SECOND * 1000L + 30 * 60_000L);
    private final ForecastViewModel viewModel = new ForecastViewModel(repository, clock);

    private static HourlyForecast hours(int count) {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        for (int h = 0; h < count; h++) {
            rows.add(new HourlyForecast.Hour(FIRST_END_SECOND + h * 3_600L, h, null));
        }
        return new HourlyForecast(rows, 0L);
    }

    @Test
    public void initLoadsOnceForTheGivenOrigin() {
        viewModel.init(ORIGIN, true);
        viewModel.init(new GeoPoint(0, 0), false);   // after rotation: ignored

        assertEquals(1, repository.asked.size());
        assertEquals(ORIGIN, repository.asked.get(0));
        assertTrue(viewModel.isReferenceArea());
        assertEquals(ForecastUiState.Status.LOADING, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void contentIsTheNextTwentyFourHoursFromNow() {
        viewModel.init(ORIGIN, false);

        repository.waiting.get(0).onSuccess(hours(26));

        ForecastUiState state = viewModel.getUiState().getValue();
        assertEquals(ForecastUiState.Status.CONTENT, state.getStatus());
        assertEquals(24, state.getHours().size());
        assertEquals(FIRST_END_SECOND + 3_600L, state.getHours().get(0).getEndEpochSecond());
    }

    @Test
    public void aFailureKeepsItsReasonAndRetryAsksAgain() {
        viewModel.init(ORIGIN, false);
        repository.waiting.get(0).onError(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.NO_NETWORK));

        assertEquals(ForecastUnavailableException.Reason.NO_NETWORK,
                viewModel.getUiState().getValue().getReason());

        viewModel.load();

        assertEquals(2, repository.asked.size());
        assertEquals(ForecastUiState.Status.LOADING, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void anUntypedFailureIsShownAsUnavailable() {
        viewModel.init(ORIGIN, false);
        repository.waiting.get(0).onError(new IllegalStateException("bug"));

        assertEquals(ForecastUnavailableException.Reason.MALFORMED,
                viewModel.getUiState().getValue().getReason());
    }
}
