package com.comp90018.storystep.domain.recommendation;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.opening.OpeningHoursEvaluator;
import com.comp90018.storystep.domain.opening.OpeningStatus;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;
import com.comp90018.storystep.domain.weather.WeatherFreshness;
import com.comp90018.storystep.domain.weather.WeatherFreshnessPolicy;
import com.comp90018.storystep.domain.weather.WeatherResult;

import java.util.ArrayList;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Deterministic REQ-21 policy. These initial weights are a testable baseline, not field tuning. */
public final class RecommendationEngine {
    public enum Mode { RECOMMENDED, NEARBY }
    public enum Reason {
        DISTANCE, INDOOR_WET, OUTDOOR_DAY, INDOOR_EVENING, SAVED, UNVISITED,
        OPEN_NOW, CLOSED_NOW
    }

    public static final double DISTANCE_WEIGHT = 0.45;
    public static final double WEATHER_WEIGHT = 0.25;
    public static final double TIME_WEIGHT = 0.10;
    public static final double SAVED_WEIGHT = 0.10;
    public static final double UNVISITED_WEIGHT = 0.10;
    public static final double OPENING_ADJUSTMENT = 0.05;
    private static final double NEUTRAL = 0.5;

    public static final class Score {
        public final double total;
        public final List<Reason> reasons;

        private Score(double total, List<Reason> reasons) {
            this.total = total;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public static final class RankedAttraction {
        public final Attraction attraction;
        public final double distanceMeters;
        public final Score score;

        private RankedAttraction(Attraction attraction, double distanceMeters, Score score) {
            this.attraction = attraction;
            this.distanceMeters = distanceMeters;
            this.score = score;
        }
    }

    /** Null preference sets mean unavailable, distinct from a successfully loaded empty set. */
    public List<RankedAttraction> rank(List<Attraction> candidates, GeoPoint origin,
            double radiusMeters, WeatherCondition weather, Integer localHour,
            Set<String> saved, Set<String> visited, Mode mode, int limit) {
        return rank(candidates, origin, radiusMeters, weather, localHour, null, null,
                saved, visited, mode, limit);
    }

    /** Rank with a complete local moment so recorded opening hours can be evaluated. */
    public List<RankedAttraction> rank(List<Attraction> candidates, GeoPoint origin,
            double radiusMeters, WeatherCondition weather, Integer localHour,
            DayOfWeek localDay, Integer localMinute,
            Set<String> saved, Set<String> visited, Mode mode, int limit) {
        List<RankedAttraction> ranked = new ArrayList<>();
        if (candidates == null || origin == null || limit <= 0) {
            return ranked;
        }
        for (Attraction attraction : candidates) {
            if (attraction == null) {
                continue;
            }
            double distance = GeoCalculator.distanceMeters(origin, attraction.getLocation());
            OpeningStatus opening = localMinute == null
                    ? OpeningStatus.UNKNOWN
                    : OpeningHoursEvaluator.evaluate(attraction.getFacts().getOpeningHours(),
                            localDay, localMinute);
            Score score = score(distance, radiusMeters, attraction.getIndoor(), weather, localHour,
                    saved == null ? null : saved.contains(attraction.getId()),
                    visited == null ? null : visited.contains(attraction.getId()), opening);
            if (score != null) {
                ranked.add(new RankedAttraction(attraction, distance, score));
            }
        }
        Comparator<RankedAttraction> nearest = Comparator
                .comparingDouble((RankedAttraction item) -> item.distanceMeters)
                .thenComparing(item -> item.attraction.getId());
        ranked.sort(mode == Mode.NEARBY ? nearest : Comparator
                .comparingDouble((RankedAttraction item) -> item.score.total).reversed()
                .thenComparing(nearest));
        return new ArrayList<>(ranked.subList(0, Math.min(limit, ranked.size())));
    }

    /** Invalid/out-of-radius distance is excluded, never assigned a misleading finite score. */
    public Score score(double distance, double radius, IndoorClassification indoor,
            WeatherCondition weather, Integer hour, Boolean saved, Boolean visited) {
        return score(distance, radius, indoor, weather, hour, saved, visited,
                OpeningStatus.UNKNOWN);
    }

    /** Base recommendation score with a bounded adjustment for reliable opening status. */
    public Score score(double distance, double radius, IndoorClassification indoor,
            WeatherCondition weather, Integer hour, Boolean saved, Boolean visited,
            OpeningStatus opening) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(radius)
                || radius <= 0 || distance > radius) {
            return null;
        }
        boolean indoors = indoor == IndoorClassification.INDOOR;
        boolean outdoors = indoor == IndoorClassification.OUTDOOR;
        double distanceScore = 1 - distance / radius;
        double weatherScore = NEUTRAL;
        double timeScore = NEUTRAL;
        List<Reason> reasons = new ArrayList<>();
        reasons.add(Reason.DISTANCE);
        if (weather != null && weather.isWet()) {
            weatherScore = indoors ? 1 : outdoors ? 0 : NEUTRAL;
            if (indoors) {
                reasons.add(Reason.INDOOR_WET);
            }
        }
        if (hour != null && hour >= 0 && hour < 24) {
            if (hour >= 8 && hour < 18 && outdoors) {
                timeScore = 1;
                reasons.add(Reason.OUTDOOR_DAY);
            } else if ((hour < 8 || hour >= 18) && indoors) {
                timeScore = 1;
                reasons.add(Reason.INDOOR_EVENING);
            }
        }
        if (Boolean.TRUE.equals(saved)) {
            reasons.add(Reason.SAVED);
        }
        if (Boolean.FALSE.equals(visited)) {
            reasons.add(Reason.UNVISITED);
        }
        double openingAdjustment = 0;
        if (opening == OpeningStatus.OPEN) {
            openingAdjustment = OPENING_ADJUSTMENT;
            reasons.add(Reason.OPEN_NOW);
        } else if (opening == OpeningStatus.CLOSED) {
            openingAdjustment = -OPENING_ADJUSTMENT;
            reasons.add(Reason.CLOSED_NOW);
        }
        double savedScore = saved == null ? NEUTRAL : saved ? 1 : 0;
        double unvisitedScore = visited == null ? NEUTRAL : visited ? 0 : 1;
        double total = DISTANCE_WEIGHT * distanceScore + WEATHER_WEIGHT * weatherScore
                + TIME_WEIGHT * timeScore + SAVED_WEIGHT * savedScore
                + UNVISITED_WEIGHT * unvisitedScore + openingAdjustment;
        return new Score(Math.max(0, Math.min(1, total)), reasons);
    }

    /** Re-evaluated as time passes; old-area, stale, unknown and future data are neutral. */
    public static WeatherCondition usableWeather(WeatherResult result, GeoPoint origin, long now) {
        if (result == null || origin == null || result.getFreshness() != WeatherFreshness.FRESH) {
            return WeatherCondition.UNKNOWN;
        }
        WeatherSnapshot snapshot = result.getSnapshot();
        long maxAge = WeatherFreshnessPolicy.defaultPolicy().getRefreshAfterMillis();
        if (!WeatherAreaBucket.of(origin).key().equals(snapshot.getAreaKey())
                || now < snapshot.getFetchedAtMillis() || now < snapshot.getObservedAtMillis()
                || now - snapshot.getFetchedAtMillis() > maxAge
                || now - snapshot.getObservedAtMillis() > maxAge) {
            return WeatherCondition.UNKNOWN;
        }
        return snapshot.getCondition();
    }
}
