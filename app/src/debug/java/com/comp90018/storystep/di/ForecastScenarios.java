package com.comp90018.storystep.di;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.SunTimes;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Debug build only: simulated three-model ensembles for Melbourne, shaped like the real response
 * (ICON 40 hourly members, ECMWF 51 and GFS 31 in three-hour blocks, 34 hourly rows from the
 * current hour, sunrise 06:40 and sunset 19:30 local).
 *
 * <p>Each scenario gives, per local hour and model, the share of members that rain (1 mm). The
 * same members stay wet from hour to hour, as in a real ensemble, so a walk is not counted wet by
 * different members in different hours.
 */
final class ForecastScenarios {

    static final String OFF = "off";
    static final List<String> NAMES = Arrays.asList(OFF, "afternoon_showers", "rain_all_day",
            "dry_only_at_night", "models_disagree");

    private static final TimeZone MELBOURNE = TimeZone.getTimeZone("Australia/Melbourne");
    private static final long HOUR = 3_600_000L;
    private static final int ROWS = 34;

    private ForecastScenarios() {
    }

    static boolean isKnown(String name) {
        return NAMES.contains(name);
    }

    /** The simulated ensemble, or null for "off" or an unknown name. */
    @Nullable
    static EnsemblePrecipitation build(String name, long nowMillis) {
        if (OFF.equals(name) || !isKnown(name)) {
            return null;
        }
        long firstEnd = (nowMillis / HOUR) * HOUR;   // the row ending at the current hour's start
        long[] ends = new long[ROWS];
        for (int r = 0; r < ROWS; r++) {
            ends[r] = (firstEnd + r * HOUR) / 1000L;
        }
        EnsemblePrecipitation.Model icon = model(name, "ICON", 0, 40, 1, ends);
        EnsemblePrecipitation.Model ecmwf = model(name, "ECMWF", 1, 51, 3, ends);
        EnsemblePrecipitation.Model gfs = model(name, "GFS", 2, 31, 3, ends);
        return new EnsemblePrecipitation(ends, Arrays.asList(icon, ecmwf, gfs),
                new ForecastPlace(MELBOURNE.getID(), MELBOURNE.getOffset(nowMillis) / 1000,
                        sun(nowMillis)),
                nowMillis);
    }

    private static EnsemblePrecipitation.Model model(String scenario, String name, int index,
                                                     int members, int blockHours, long[] ends) {
        double[][] mm = new double[members][ends.length];
        for (int r = 0; r < ends.length; r++) {
            // A three-hour model reads its share at the block's last hour and repeats it.
            long blockEnd = blockHours == 1 ? ends[r]
                    : (Math.floorDiv(ends[r] / 3_600L - 1, blockHours) + 1) * blockHours * 3_600L;
            int hour = localHour(blockEnd * 1000L - HOUR / 2);
            int wet = (int) Math.round(share(scenario, index, hour) * members);
            for (int m = 0; m < wet; m++) {
                mm[m][r] = blockHours == 1 ? 1.0 : 1.0 / blockHours;
            }
        }
        return new EnsemblePrecipitation.Model(name, blockHours, mm);
    }

    /** Share of a model's members raining in the hour that starts at this local hour. */
    private static double share(String scenario, int model, int hour) {
        boolean day = hour >= 7 && hour < 19;
        switch (scenario) {
            case "afternoon_showers":
                // Dry morning, showers 13:00-18:00 local.
                return hour >= 13 && hour < 18 ? 0.70 : 0.03;
            case "rain_all_day":
                // Wet throughout; a slightly lighter spell 09:00-11:00.
                return hour >= 9 && hour < 11 ? 0.62 : 0.80;
            case "dry_only_at_night":
                return day ? 0.75 : 0.02;
            case "models_disagree":
                // Scattered showers: ICON light, ECMWF heavy, GFS in between, peaking mid-afternoon.
                double peak = Math.max(0, 1 - Math.abs(hour - 15) / 6.0);
                double[] scale = {0.25, 0.75, 0.45};
                return 0.05 + peak * scale[model];
            default:
                return 0;
        }
    }

    private static int localHour(long millis) {
        Calendar calendar = Calendar.getInstance(MELBOURNE);
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    /** Sunrise 06:40 and sunset 19:30 local, for yesterday through the day after tomorrow. */
    private static SunTimes sun(long nowMillis) {
        long[] rise = new long[4];
        long[] set = new long[4];
        for (int d = 0; d < 4; d++) {
            Calendar day = Calendar.getInstance(MELBOURNE);
            day.setTimeInMillis(nowMillis);
            day.add(Calendar.DAY_OF_MONTH, d - 1);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            day.set(Calendar.HOUR_OF_DAY, 6);
            day.set(Calendar.MINUTE, 40);
            rise[d] = day.getTimeInMillis() / 1000L;
            day.set(Calendar.HOUR_OF_DAY, 19);
            day.set(Calendar.MINUTE, 30);
            set[d] = day.getTimeInMillis() / 1000L;
        }
        return SunTimes.of(rise, set);
    }
}
