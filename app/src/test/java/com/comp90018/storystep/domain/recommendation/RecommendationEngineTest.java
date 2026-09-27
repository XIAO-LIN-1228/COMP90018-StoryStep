package com.comp90018.storystep.domain.recommendation;

import static org.junit.Assert.*;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.opening.OpeningStatus;
import com.comp90018.storystep.domain.weather.*;
import java.time.DayOfWeek;
import java.util.*;
import org.junit.Test;

public class RecommendationEngineTest {
    private final RecommendationEngine engine = new RecommendationEngine();
    private static final GeoPoint ORIGIN = new GeoPoint(-37.7963, 144.9614);
    private static final long NOW = 1_700_000_000_000L;

    @Test public void equalDistanceRainFixtureMatchesAgreedScores() {
        assertEquals(.76, engine.score(200, 1000, IndoorClassification.INDOOR,
                WeatherCondition.RAIN, null, false, false).total, 1e-9);
        assertEquals(.51, engine.score(200, 1000, IndoorClassification.OUTDOOR,
                WeatherCondition.RAIN, null, false, false).total, 1e-9);
        for (IndoorClassification type : IndoorClassification.values()) {
            assertEquals(.635, engine.score(200, 1000, type, WeatherCondition.UNKNOWN,
                    null, false, false).total, 1e-9);
        }
    }

    @Test public void unavailableComponentsAreNeutralAndHaveNoInventedReasons() {
        RecommendationEngine.Score score = engine.score(500, 1000, IndoorClassification.UNKNOWN,
                WeatherCondition.UNKNOWN, null, null, null);
        assertEquals(.5, score.total, 1e-9);
        assertEquals(Collections.singletonList(RecommendationEngine.Reason.DISTANCE), score.reasons);
    }

    @Test public void invalidOrOutsideCandidatesAreExcludedAndBoundaryIsIncluded() {
        for (double distance : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 1001}) {
            assertNull(engine.score(distance, 1000, IndoorClassification.INDOOR,
                    WeatherCondition.RAIN, 12, true, false));
        }
        for (double radius : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertNull(engine.score(10, radius, IndoorClassification.INDOOR,
                    WeatherCondition.RAIN, 12, true, false));
        }
        assertNotNull(engine.score(1000, 1000, IndoorClassification.UNKNOWN, null, null, null, null));
    }

    @Test public void preferencesHaveIndependentBoundedEffects() {
        double base = engine.score(100, 1000, IndoorClassification.UNKNOWN, null, null, false, true).total;
        assertEquals(base + .1, engine.score(100, 1000, IndoorClassification.UNKNOWN,
                null, null, true, true).total, 1e-9);
        assertEquals(base + .1, engine.score(100, 1000, IndoorClassification.UNKNOWN,
                null, null, false, false).total, 1e-9);
        for (IndoorClassification type : IndoorClassification.values()) {
            for (WeatherCondition weather : WeatherCondition.values()) {
                for (int hour = 0; hour < 24; hour++) {
                    double value = engine.score(0, 1000, type, weather, hour, true, false).total;
                    assertTrue(Double.isFinite(value) && value >= 0 && value <= 1);
                }
            }
        }
    }

    @Test public void reliableOpeningStatusHasSmallBoundedEffectAndExplicitReason() {
        double unknown = engine.score(100, 1000, IndoorClassification.UNKNOWN, null,
                null, false, true, OpeningStatus.UNKNOWN).total;
        RecommendationEngine.Score open = engine.score(100, 1000,
                IndoorClassification.UNKNOWN, null, null, false, true, OpeningStatus.OPEN);
        RecommendationEngine.Score closed = engine.score(100, 1000,
                IndoorClassification.UNKNOWN, null, null, false, true, OpeningStatus.CLOSED);
        assertEquals(unknown + RecommendationEngine.OPENING_ADJUSTMENT, open.total, 1e-9);
        assertEquals(unknown - RecommendationEngine.OPENING_ADJUSTMENT, closed.total, 1e-9);
        assertTrue(open.reasons.contains(RecommendationEngine.Reason.OPEN_NOW));
        assertTrue(closed.reasons.contains(RecommendationEngine.Reason.CLOSED_NOW));
        assertEquals(1, engine.score(0, 1000, IndoorClassification.INDOOR,
                WeatherCondition.RAIN, 20, true, false, OpeningStatus.OPEN).total, 0);
    }

    @Test public void recommendedUsesOpeningStatusButNearbyRemainsDistanceOnly() {
        Attraction closed = place("a-closed", IndoorClassification.UNKNOWN, ORIGIN)
                .withFacts(new PlaceFacts("Su 09:00-11:00", null, null, null, null));
        Attraction unknown = place("b-unknown", IndoorClassification.UNKNOWN, ORIGIN)
                .withFacts(new PlaceFacts("PH off", null, null, null, null));
        Attraction open = place("c-open", IndoorClassification.UNKNOWN, ORIGIN)
                .withFacts(new PlaceFacts("Su 11:00-13:00", null, null, null, null));
        List<Attraction> candidates = Arrays.asList(closed, unknown, open);
        List<RecommendationEngine.RankedAttraction> recommended = engine.rank(candidates,
                ORIGIN, 1000, null, 12, DayOfWeek.SUNDAY, 12 * 60,
                null, null, RecommendationEngine.Mode.RECOMMENDED, 30);
        assertEquals("c-open", recommended.get(0).attraction.getId());
        assertEquals("b-unknown", recommended.get(1).attraction.getId());
        assertEquals("a-closed", recommended.get(2).attraction.getId());
        List<RecommendationEngine.RankedAttraction> nearby = engine.rank(candidates,
                ORIGIN, 1000, null, 12, DayOfWeek.SUNDAY, 12 * 60,
                null, null, RecommendationEngine.Mode.NEARBY, 30);
        assertEquals("a-closed", nearby.get(0).attraction.getId());
    }

    @Test public void timeBoundariesArePreferencesNotOpeningClaims() {
        assertTrue(engine.score(0, 1000, IndoorClassification.INDOOR, null, 7, null, null)
                .reasons.contains(RecommendationEngine.Reason.INDOOR_EVENING));
        assertFalse(engine.score(0, 1000, IndoorClassification.INDOOR, null, 8, null, null)
                .reasons.contains(RecommendationEngine.Reason.INDOOR_EVENING));
        assertTrue(engine.score(0, 1000, IndoorClassification.OUTDOOR, null, 17, null, null)
                .reasons.contains(RecommendationEngine.Reason.OUTDOOR_DAY));
        assertFalse(engine.score(0, 1000, IndoorClassification.OUTDOOR, null, 18, null, null)
                .reasons.contains(RecommendationEngine.Reason.OUTDOOR_DAY));
        assertEquals(engine.score(0, 1000, IndoorClassification.OUTDOOR, null, null, null, null).total,
                engine.score(0, 1000, IndoorClassification.OUTDOOR, null, 24, null, null).total, 0);
    }

    @Test public void scoringPrecedesLimitAndDoesNotMutateInputOrUsePopularity() {
        List<Attraction> places = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            places.add(place("out" + i, IndoorClassification.OUTDOOR, ORIGIN).withWikidata(null, 500));
        }
        places.add(place("indoor", IndoorClassification.INDOOR, ORIGIN));
        List<RecommendationEngine.RankedAttraction> result = engine.rank(places, ORIGIN, 1000,
                WeatherCondition.RAIN, 12, null, null, RecommendationEngine.Mode.RECOMMENDED, 30);
        assertEquals("indoor", result.get(0).attraction.getId());
        assertEquals("out0", places.get(0).getId());
        assertEquals(30, result.size());
    }

    @Test public void nearbyIgnoresContextAndTiesAreStableUnderPermutation() {
        Attraction near = place("near", IndoorClassification.OUTDOOR, ORIGIN);
        Attraction far = place("far", IndoorClassification.INDOOR, new GeoPoint(-37.795, 144.9614));
        assertEquals("near", engine.rank(Arrays.asList(far, near), ORIGIN, 1000,
                WeatherCondition.RAIN, 20, Collections.singleton("far"), null,
                RecommendationEngine.Mode.NEARBY, 30).get(0).attraction.getId());
        Attraction a = place("a", IndoorClassification.UNKNOWN, ORIGIN);
        Attraction b = place("b", IndoorClassification.UNKNOWN, ORIGIN);
        for (RecommendationEngine.Mode mode : RecommendationEngine.Mode.values()) {
            assertEquals("a", engine.rank(Arrays.asList(b, a), ORIGIN, 1000,
                    null, null, null, null, mode, 30).get(0).attraction.getId());
            assertEquals("a", engine.rank(Arrays.asList(a, b), ORIGIN, 1000,
                    null, null, null, null, mode, 30).get(0).attraction.getId());
        }
    }

    @Test public void wrongAreaStaleFutureAndOldObservationsAreNeutral() {
        WeatherSnapshot snapshot = new WeatherSnapshot(WeatherAreaBucket.of(ORIGIN).key(),
                WeatherCondition.RAIN, 10, NOW, NOW, WeatherSnapshot.Provenance.CACHE);
        WeatherResult fresh = new WeatherResult(snapshot, WeatherFreshness.FRESH);
        assertEquals(WeatherCondition.RAIN, RecommendationEngine.usableWeather(fresh, ORIGIN, NOW));
        assertEquals(WeatherCondition.RAIN, RecommendationEngine.usableWeather(fresh, ORIGIN, NOW + 30 * 60_000L));
        assertEquals(WeatherCondition.UNKNOWN, RecommendationEngine.usableWeather(fresh, ORIGIN, NOW + 30 * 60_000L + 1));
        assertEquals(WeatherCondition.UNKNOWN, RecommendationEngine.usableWeather(fresh, ORIGIN, NOW - 1));
        assertEquals(WeatherCondition.UNKNOWN, RecommendationEngine.usableWeather(fresh, new GeoPoint(0, 0), NOW));
        assertEquals(WeatherCondition.UNKNOWN, RecommendationEngine.usableWeather(
                new WeatherResult(snapshot, WeatherFreshness.STALE), ORIGIN, NOW));
        WeatherSnapshot oldObservation = new WeatherSnapshot(snapshot.getAreaKey(), WeatherCondition.RAIN,
                10, NOW - 60 * 60_000L, NOW, WeatherSnapshot.Provenance.NETWORK);
        assertEquals(WeatherCondition.UNKNOWN, RecommendationEngine.usableWeather(
                new WeatherResult(oldObservation, WeatherFreshness.FRESH), ORIGIN, NOW));
    }

    @Test public void emptyCandidatesAndUnknownWeatherRemainUsable() {
        assertTrue(engine.rank(Collections.emptyList(), ORIGIN, 1000, null, null,
                null, null, RecommendationEngine.Mode.RECOMMENDED, 30).isEmpty());
        assertFalse(engine.rank(Collections.singletonList(place("a", IndoorClassification.UNKNOWN, ORIGIN)),
                ORIGIN, 1000, null, null, null, null, RecommendationEngine.Mode.RECOMMENDED, 30).isEmpty());
    }

    private static Attraction place(String id, IndoorClassification type, GeoPoint point) {
        return Attraction.seeded(id, id, AttractionCategory.PARK, "", type, point);
    }
}
