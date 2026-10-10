package com.comp90018.storystep.domain.forecast;

import com.comp90018.storystep.domain.model.WeatherCondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Open-Meteo's own hourly chance of rain and condition for one weather area: what the forecast
 * page lists.
 *
 * <p>Either field may be missing for an hour; that hour is still listed, with the field blank.
 */
public final class HourlyForecast {

    /** One hour of the forecast. */
    public static final class Hour {

        private final long endEpochSecond;
        private final Integer precipitationProbability;
        private final WeatherCondition condition;
        private final Boolean daylight;

        public Hour(long endEpochSecond, Integer precipitationProbability,
                    WeatherCondition condition) {
            this(endEpochSecond, precipitationProbability, condition, null);
        }

        public Hour(long endEpochSecond, Integer precipitationProbability,
                    WeatherCondition condition, Boolean daylight) {
            this.endEpochSecond = endEpochSecond;
            this.precipitationProbability = precipitationProbability;
            this.condition = condition;
            this.daylight = daylight;
        }

        /** When the hour this row describes ends; Open-Meteo's own timestamp. */
        public long getEndEpochSecond() {
            return endEpochSecond;
        }

        public long getStartMillis() {
            return HourlyBuckets.startMillis(endEpochSecond);
        }

        public long getEndMillis() {
            return HourlyBuckets.endMillis(endEpochSecond);
        }

        /** Percent, 0 to 100, or null when the provider gave none for this hour. */
        public Integer getPrecipitationProbability() {
            return precipitationProbability;
        }

        /** The condition, or null when the provider gave no code for this hour. */
        public WeatherCondition getCondition() {
            return condition;
        }

        /**
         * Whether the middle of the hour is between that day's sunrise and sunset, or null when
         * unknown. Only chooses a day or night icon; the provider's weather code is the same by
         * day and by night.
         */
        public Boolean getDaylight() {
            return daylight;
        }
    }

    private final List<Hour> hours;
    private final long fetchedAtMillis;
    private final ForecastPlace place;

    public HourlyForecast(List<Hour> hours, long fetchedAtMillis) {
        this(hours, fetchedAtMillis, ForecastPlace.unknown());
    }

    public HourlyForecast(List<Hour> hours, long fetchedAtMillis, ForecastPlace place) {
        Objects.requireNonNull(hours, "hours");
        long[] ends = new long[hours.size()];
        for (int i = 0; i < ends.length; i++) {
            Hour hour = Objects.requireNonNull(hours.get(i), "hour");
            Integer probability = hour.getPrecipitationProbability();
            if (probability != null && (probability < 0 || probability > 100)) {
                throw new IllegalArgumentException("probability out of range: " + probability);
            }
            ends[i] = hour.getEndEpochSecond();
        }
        HourlyBuckets.requireHourly(ends);
        this.hours = Collections.unmodifiableList(new ArrayList<>(hours));
        this.fetchedAtMillis = fetchedAtMillis;
        this.place = Objects.requireNonNull(place, "place");
    }

    /** The location's timezone and sun times; times on the page are shown in this zone. */
    public ForecastPlace getPlace() {
        return place;
    }

    public List<Hour> getHours() {
        return hours;
    }

    public long getFetchedAtMillis() {
        return fetchedAtMillis;
    }

    /**
     * The hours that have not ended yet at {@code nowMillis}, at most {@code limit} of them. The
     * hour in progress is included; hours already over are not.
     */
    public List<Hour> upcoming(long nowMillis, int limit) {
        List<Hour> result = new ArrayList<>();
        for (Hour hour : hours) {
            if (result.size() >= limit) {
                break;
            }
            if (hour.getEndMillis() > nowMillis) {
                result.add(hour);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
