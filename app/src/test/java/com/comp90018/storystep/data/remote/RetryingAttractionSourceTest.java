package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RetryingAttractionSourceTest {

    @Test
    public void timeoutRetriesThenReturnsSuccess() {
        CountingSource source = new CountingSource();
        RecordingSleeper sleeper = new RecordingSleeper();

        RetryingAttractionSource retrying =
                new RetryingAttractionSource(
                        source,
                        new NetworkRetryPolicy(),
                        sleeper);

        retrying.fetchNearby(
                new GeoPoint(-37.8, 144.9),
                1_000.0,
                10);

        assertEquals(2, source.calls);
        assertEquals(500L, sleeper.totalDelay);
    }

    private static final class CountingSource implements AttractionSource {

        private int calls;

        @Override
        public List<Attraction> fetchNearby(GeoPoint center,
                                            double radiusMeters,
                                            int limit) {
            calls++;

            if (calls == 1) {
                throw new AttractionUnavailableException(
                        AttractionUnavailableException.Reason.TIMEOUT);
            }

            return Collections.emptyList();
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