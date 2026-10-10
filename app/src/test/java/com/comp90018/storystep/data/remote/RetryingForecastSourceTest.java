package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The shared retry policy applied to forecast fetches, without waiting. */
public class RetryingForecastSourceTest {

    private static final GeoPoint CENTRE = new GeoPoint(-37.825, 144.975);

    /** Fails with the given reasons in turn, then answers "ok". */
    private static final class ScriptedSource implements ForecastSource<String> {
        private final List<ForecastUnavailableException> failures;
        int calls;

        ScriptedSource(ForecastUnavailableException... failures) {
            this.failures = new ArrayList<>(Arrays.asList(failures));
        }

        @Override
        public String fetch(GeoPoint cellCentre, long fetchedAtMillis) {
            calls++;
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            return "ok";
        }
    }

    private static final class RecordingSleeper implements Sleeper {
        final List<Long> delays = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            delays.add(millis);
        }
    }

    private static ForecastUnavailableException failure(ForecastUnavailableException.Reason reason) {
        return new ForecastUnavailableException(reason);
    }

    private static RetryingForecastSource<String> retrying(ScriptedSource source,
                                                           RecordingSleeper sleeper) {
        return new RetryingForecastSource<>(source, new NetworkRetryPolicy(), sleeper);
    }

    @Test
    public void timeoutsAreRetriedWithBackoff() {
        ScriptedSource source = new ScriptedSource(
                failure(ForecastUnavailableException.Reason.TIMEOUT),
                failure(ForecastUnavailableException.Reason.TIMEOUT));
        RecordingSleeper sleeper = new RecordingSleeper();

        assertEquals("ok", retrying(source, sleeper).fetch(CENTRE, 0L));
        assertEquals(3, source.calls);
        assertEquals(Arrays.asList(500L, 1_000L), sleeper.delays);
    }

    @Test
    public void giveUpAfterThreeAttempts() {
        ScriptedSource source = new ScriptedSource(
                failure(ForecastUnavailableException.Reason.NO_NETWORK),
                failure(ForecastUnavailableException.Reason.NO_NETWORK),
                failure(ForecastUnavailableException.Reason.NO_NETWORK));

        assertGivesUp(source, ForecastUnavailableException.Reason.NO_NETWORK, 3);
    }

    @Test
    public void malformedAndUnusableAreNotRetried() {
        assertGivesUp(new ScriptedSource(failure(ForecastUnavailableException.Reason.MALFORMED)),
                ForecastUnavailableException.Reason.MALFORMED, 1);
        assertGivesUp(new ScriptedSource(failure(ForecastUnavailableException.Reason.UNUSABLE)),
                ForecastUnavailableException.Reason.UNUSABLE, 1);
    }

    @Test
    public void aClientErrorIsNotRetried() {
        assertGivesUp(new ScriptedSource(new ForecastUnavailableException(
                        ForecastUnavailableException.Reason.HTTP_ERROR, null, 400, -1L)),
                ForecastUnavailableException.Reason.HTTP_ERROR, 1);
    }

    @Test
    public void throttlingIsRetriedOnceWithAClampedDelay() {
        ScriptedSource source = new ScriptedSource(
                new ForecastUnavailableException(
                        ForecastUnavailableException.Reason.RATE_LIMITED, null, 429, 30_000L),
                new ForecastUnavailableException(
                        ForecastUnavailableException.Reason.RATE_LIMITED, null, 429, 30_000L));
        RecordingSleeper sleeper = new RecordingSleeper();
        try {
            retrying(source, sleeper).fetch(CENTRE, 0L);
            fail("expected a failure");
        } catch (ForecastUnavailableException expected) {
            assertEquals(2, source.calls);
            assertEquals(Arrays.asList(5_000L), sleeper.delays);
        }
    }

    private static void assertGivesUp(ScriptedSource source,
                                      ForecastUnavailableException.Reason reason, int calls) {
        try {
            retrying(source, new RecordingSleeper()).fetch(CENTRE, 0L);
            fail("expected a failure");
        } catch (ForecastUnavailableException expected) {
            assertEquals(reason, expected.getReason());
            assertEquals(calls, source.calls);
        }
    }
}
