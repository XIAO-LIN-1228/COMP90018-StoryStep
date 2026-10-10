package com.comp90018.storystep.domain.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Hourly precipitation from every member of one or more ensemble models, for one weather area.
 *
 * <p>Each member is one complete, internally consistent run of its model. Keeping the members
 * apart, rather than a per-hour chance of rain, is what lets the chance of rain over a whole walk
 * be counted directly: a member that rains at 14:00 and at 15:00 is one wet scenario, not two
 * independent ones.
 *
 * <p>Models differ in time resolution. A model whose native step is three hours is delivered
 * hourly with each three-hour total spread evenly across its hours, so its rows only mean
 * something as a block; {@link Model#getBlockHours()} records that.
 *
 * <p>A value the provider left empty is held as {@link Double#NaN}. This class keeps the shape
 * consistent; whether enough data is present for advice is decided by {@link DepartureAdvisor}.
 */
public final class EnsemblePrecipitation {

    /** One model's members on the shared hourly timeline. */
    public static final class Model {

        private final String name;
        private final int blockHours;
        private final double[][] memberMm;

        /**
         * @param name       the provider's model identifier, for display and logs
         * @param blockHours the model's native step in hours: 1, or 3 for spread blocks
         * @param memberMm   millimetres per member per hour, {@code [member][hour]}
         */
        public Model(String name, int blockHours, double[][] memberMm) {
            this.name = Objects.requireNonNull(name, "name");
            if (blockHours != 1 && blockHours != 3) {
                throw new IllegalArgumentException("unsupported block size: " + blockHours);
            }
            this.blockHours = blockHours;
            Objects.requireNonNull(memberMm, "memberMm");
            double[][] copy = new double[memberMm.length][];
            for (int m = 0; m < memberMm.length; m++) {
                copy[m] = Objects.requireNonNull(memberMm[m], "member").clone();
            }
            this.memberMm = copy;
        }

        public String getName() {
            return name;
        }

        public int getBlockHours() {
            return blockHours;
        }

        public int memberCount() {
            return memberMm.length;
        }

        /** Millimetres for one member in one hour; NaN when the provider gave none. */
        public double millimetres(int member, int hour) {
            return memberMm[member][hour];
        }

        int hourCount() {
            return memberMm.length == 0 ? -1 : memberMm[0].length;
        }
    }

    private final long[] endEpochSeconds;
    private final List<Model> models;
    private final ForecastPlace place;
    private final long fetchedAtMillis;

    /**
     * @param endEpochSeconds end of each hour, one hour apart (Open-Meteo's timestamps)
     * @param models          at least one model, each with every member one row per hour
     * @param place           the location's timezone and sunrise and sunset
     */
    public EnsemblePrecipitation(long[] endEpochSeconds, List<Model> models, ForecastPlace place,
                                 long fetchedAtMillis) {
        Objects.requireNonNull(endEpochSeconds, "endEpochSeconds");
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(place, "place");
        HourlyBuckets.requireHourly(endEpochSeconds);
        if (models.isEmpty()) {
            throw new IllegalArgumentException("no models");
        }
        for (Model model : models) {
            Objects.requireNonNull(model, "model");
            int hours = model.hourCount();
            if (hours != -1 && hours != endEpochSeconds.length) {
                throw new IllegalArgumentException(model.getName() + " length differs from hours");
            }
            for (int m = 0; m < model.memberCount(); m++) {
                if (model.memberMm[m].length != endEpochSeconds.length) {
                    throw new IllegalArgumentException(model.getName() + " member " + m
                            + " length differs from hours");
                }
            }
        }
        this.endEpochSeconds = endEpochSeconds.clone();
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
        this.place = place;
        this.fetchedAtMillis = fetchedAtMillis;
    }

    public int hourCount() {
        return endEpochSeconds.length;
    }

    public long endEpochSecond(int hour) {
        return endEpochSeconds[hour];
    }

    public List<Model> getModels() {
        return models;
    }

    /** The location's timezone and sun times. */
    public ForecastPlace getPlace() {
        return place;
    }

    /** End of the last hour covered: no trip may finish after this. */
    public long coverageEndMillis() {
        return HourlyBuckets.endMillis(endEpochSeconds[endEpochSeconds.length - 1]);
    }

    public long getFetchedAtMillis() {
        return fetchedAtMillis;
    }
}
