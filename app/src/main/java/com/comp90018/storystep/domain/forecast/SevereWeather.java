package com.comp90018.storystep.domain.forecast;

/**
 * Two-level severe-weather flags for a walk, from the deterministic hourly forecast (decision
 * D-08 §4.6). Thresholds follow public criteria (BoM severe thunderstorm and damaging/destructive
 * winds, Beaufort, NWS heat index, UTCI); the result is forecast-based guidance, never a warning.
 */
public final class SevereWeather {

    /** How serious a walk's worst hour is. */
    public enum Level { NONE, CAUTION, SEVERE }

    /** What the flag is about, in the order one is named when several apply at the same level. */
    public enum Hazard { DESTRUCTIVE_WIND, HAIL, THUNDERSTORM, WIND, SNOW, HEAT, COLD }

    /** Early warning for walkers, below the Victorian pedestrian "unsafe" gust of 72 km/h. */
    public static final double CAUTION_GUST_KMH = 62;
    public static final double CAUTION_MEAN_KMH = 50;     // Beaufort 7 mean, hard to walk against
    public static final double SEVERE_GUST_KMH = 90;      // BoM damaging gust
    public static final double SEVERE_MEAN_KMH = 63;      // BoM damaging sustained wind
    public static final double DESTRUCTIVE_GUST_KMH = 125; // BoM destructive gust; TC Category 2
    public static final double DESTRUCTIVE_MEAN_KMH = 89;  // BoM destructive sustained wind
    public static final double CAUTION_HEAT_C = 32;       // NWS heat index Extreme Caution
    public static final double SEVERE_HEAT_C = 39;        // NWS heat index Danger
    public static final double CAUTION_COLD_C = 0;        // UTCI moderate cold stress
    public static final double SEVERE_COLD_C = -13;       // UTCI strong cold stress

    /** One flag: its level, its hazard and the figure to show (km/h or °C; NaN when none). */
    public static final class Flag {

        public static final Flag NONE = new Flag(Level.NONE, null, Double.NaN);

        private final Level level;
        private final Hazard hazard;
        private final double value;

        Flag(Level level, Hazard hazard, double value) {
            this.level = level;
            this.hazard = hazard;
            this.value = value;
        }

        public Level getLevel() {
            return level;
        }

        /** Null when the level is {@link Level#NONE}. */
        public Hazard getHazard() {
            return hazard;
        }

        /** The gust, wind or feels-like figure behind the flag; NaN for code-only hazards. */
        public double getValue() {
            return value;
        }

        /** The more serious of two flags; on a tie, the hazard named first in {@link Hazard}. */
        Flag worse(Flag other) {
            if (other.level.ordinal() != level.ordinal()) {
                return other.level.ordinal() > level.ordinal() ? other : this;
            }
            if (level == Level.NONE) {
                return this;
            }
            if (other.hazard != hazard) {
                return other.hazard.ordinal() < hazard.ordinal() ? other : this;
            }
            // Same hazard: keep the more extreme figure to show.
            boolean colder = hazard == Hazard.COLD;
            return (colder ? other.value < value : other.value > value) ? other : this;
        }
    }

    private SevereWeather() {
    }

    /** The worst flag of one hour. */
    public static Flag assess(HourlyForecast.Hour hour) {
        Integer code = hour.getWeatherCode();
        double gust = orNaN(hour.getGustKmh());
        double mean = orNaN(hour.getWindKmh());
        double feels = orNaN(hour.getApparentC());
        Flag flag = Flag.NONE;

        if (gust >= DESTRUCTIVE_GUST_KMH || mean >= DESTRUCTIVE_MEAN_KMH) {
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.DESTRUCTIVE_WIND,
                    gust >= DESTRUCTIVE_GUST_KMH ? gust : mean));
        }
        if (code != null && (code == 96 || code == 99)) {
            // Any forecast hail is severe: its size is never forecast.
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.HAIL, Double.NaN));
        }
        if (code != null && code == 97) {
            // Heavy thunderstorm, which models without hail report instead of 96/99.
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.THUNDERSTORM, gust));
        }
        if (code != null && code == 95) {
            flag = flag.worse(gust >= SEVERE_GUST_KMH
                    ? new Flag(Level.SEVERE, Hazard.THUNDERSTORM, gust)
                    : new Flag(Level.CAUTION, Hazard.THUNDERSTORM, Double.NaN));
        }
        if (gust >= SEVERE_GUST_KMH || mean >= SEVERE_MEAN_KMH) {
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.WIND, gust >= SEVERE_GUST_KMH
                    ? gust : mean));
        } else if (gust >= CAUTION_GUST_KMH || mean >= CAUTION_MEAN_KMH) {
            flag = flag.worse(new Flag(Level.CAUTION, Hazard.WIND,
                    gust >= CAUTION_GUST_KMH ? gust : mean));
        }
        if (code != null && (code == 75 || code == 86)) {
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.SNOW, Double.NaN));
        } else if (code != null && (code == 71 || code == 73 || code == 77 || code == 85)) {
            flag = flag.worse(new Flag(Level.CAUTION, Hazard.SNOW, Double.NaN));
        }
        if (feels >= SEVERE_HEAT_C) {
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.HEAT, feels));
        } else if (feels >= CAUTION_HEAT_C) {
            flag = flag.worse(new Flag(Level.CAUTION, Hazard.HEAT, feels));
        }
        if (feels <= SEVERE_COLD_C) {
            flag = flag.worse(new Flag(Level.SEVERE, Hazard.COLD, feels));
        } else if (feels <= CAUTION_COLD_C) {
            flag = flag.worse(new Flag(Level.CAUTION, Hazard.COLD, feels));
        }
        return flag;
    }

    /**
     * The worst flag of every hour the walk [start, end) touches, or null when the forecast does
     * not cover the whole walk (the walk is then unchecked, not safe).
     *
     * <p>Gusts are the maximum of the hour before a timestamp, but wind, feels-like and the weather
     * code are values at the timestamp. So the row stamped at the walk's start instant is read too:
     * it holds the conditions as the walker sets off.
     */
    public static Flag assessWalk(HourlyForecast forecast, long startMillis, long endMillis) {
        Flag worst = Flag.NONE;
        long coveredFrom = Long.MAX_VALUE;
        long coveredTo = Long.MIN_VALUE;
        for (HourlyForecast.Hour hour : forecast.getHours()) {
            if (hour.getEndMillis() == startMillis) {
                worst = worst.worse(assess(hour));
                continue;
            }
            if (!HourlyBuckets.overlaps(hour.getEndEpochSecond(), startMillis, endMillis)) {
                continue;
            }
            coveredFrom = Math.min(coveredFrom, hour.getStartMillis());
            coveredTo = Math.max(coveredTo, hour.getEndMillis());
            worst = worst.worse(assess(hour));
        }
        if (coveredFrom > startMillis || coveredTo < endMillis) {
            return null;
        }
        return worst;
    }

    private static double orNaN(Double value) {
        return value == null ? Double.NaN : value;
    }
}
