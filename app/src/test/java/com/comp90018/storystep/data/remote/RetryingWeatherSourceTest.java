package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;

import org.junit.Test;

public class RetryingWeatherSourceTest {

    @Test
    public void timeoutRetriesThenReturnsSuccess() {
        CountingSource source = new CountingSource();
        RecordingSleeper sleeper = new RecordingSleeper();

        RetryingWeatherSource retrying =
                new RetryingWeatherSource(
                        source,
                        new NetworkRetryPolicy(),
                        sleeper);

        retrying.fetch(
                new GeoPoint(-37.8, 144.9),
                "melbourne",
                1_000L);

        assertEquals(2, source.calls);
        assertEquals(500L, sleeper.totalDelay);
    }

    private static final class CountingSource implements WeatherSource {

        private int calls;

        @Override
        public WeatherSnapshot fetch(GeoPoint center,
                                     String areaKey,
                                     long fetchedAtMillis) {
            calls++;

            if (calls == 1) {
                throw new WeatherUnavailableException(
                        WeatherUnavailableException.Reason.TIMEOUT);
            }

            return new WeatherSnapshot(
                    areaKey,
                    WeatherCondition.CLEAR,
                    20.0,
                    fetchedAtMillis,
                    fetchedAtMillis,
                    WeatherSnapshot.Provenance.NETWORK);
        }
    }

    private static final class RecordingSleeper implements Sleeper {

        private long totalDelay;

        @Override
        public void sleep(long millis) {
            totalDelay += millis;
        }
    }
}