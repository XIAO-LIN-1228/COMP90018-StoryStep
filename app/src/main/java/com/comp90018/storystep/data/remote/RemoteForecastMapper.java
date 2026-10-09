package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Open-Meteo forecast responses into domain series, or null when a response cannot be
 * trusted.
 *
 * <p>A shape problem — a missing time axis, a series of the wrong length, hours that are not one
 * apart — rejects the whole response. A single missing value does not: it is kept as missing,
 * and the domain decides whether the hours it falls in matter.
 */
final class RemoteForecastMapper {

    /** A model the ensemble request asks for: its column suffix, display name and native step. */
    private static final class KnownModel {
        final String suffix;
        final String name;
        final int blockHours;

        KnownModel(String suffix, String name, int blockHours) {
            this.suffix = suffix;
            this.name = name;
            this.blockHours = blockHours;
        }
    }

    /** In the order the page names them. Columns with any other suffix are ignored. */
    private static final List<KnownModel> MODELS = Collections.unmodifiableList(Arrays.asList(
            new KnownModel("icon_seamless_eps", "ICON", 1),
            new KnownModel("ecmwf_ifs025_ensemble", "ECMWF", 3),
            new KnownModel("ncep_gefs025", "GFS", 3)));

    /** {@code precipitation}, optionally a member number, then the model suffix. */
    private static final Pattern PRECIPITATION =
            Pattern.compile("precipitation(?:_member(\\d+))?_(.+)");

    private static final String IS_DAY_PREFIX = "is_day_";

    private RemoteForecastMapper() {
    }

    @Nullable
    static HourlyForecast toHourly(@Nullable HourlyForecastDto dto, long fetchedAtMillis) {
        if (dto == null || dto.hourly == null || dto.hourly.time == null
                || dto.hourly.time.isEmpty()) {
            return null;
        }
        List<Long> time = dto.hourly.time;
        List<Integer> probability = dto.hourly.precipitationProbability;
        List<Integer> codes = dto.hourly.weatherCode;
        List<Integer> isDay = dto.hourly.isDay;
        if ((probability != null && probability.size() != time.size())
                || (codes != null && codes.size() != time.size())
                || (isDay != null && isDay.size() != time.size())) {
            return null;
        }
        List<HourlyForecast.Hour> hours = new ArrayList<>(time.size());
        for (int i = 0; i < time.size(); i++) {
            Long end = time.get(i);
            if (end == null) {
                return null;
            }
            Integer code = codes == null ? null : codes.get(i);
            Integer day = isDay == null ? null : isDay.get(i);
            hours.add(new HourlyForecast.Hour(end,
                    probability == null ? null : probability.get(i),
                    code == null ? null : RemoteWeatherMapper.toCondition(code),
                    day == null ? null : day == 1));
        }
        try {
            return new HourlyForecast(hours, fetchedAtMillis);
        } catch (IllegalArgumentException inconsistent) {
            return null;
        }
    }

    @Nullable
    static EnsemblePrecipitation toEnsemble(@Nullable JsonObject body, long fetchedAtMillis) {
        try {
            return readEnsemble(body, fetchedAtMillis);
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException
                 | UnsupportedOperationException malformed) {
            return null;
        }
    }

    @Nullable
    private static EnsemblePrecipitation readEnsemble(@Nullable JsonObject body,
                                                      long fetchedAtMillis) {
        if (body == null || !body.has("hourly") || !body.get("hourly").isJsonObject()) {
            return null;
        }
        JsonObject hourly = body.getAsJsonObject("hourly");
        JsonArray timeArray = array(hourly, "time");
        if (timeArray == null || timeArray.size() == 0) {
            return null;
        }
        int hours = timeArray.size();
        long[] time = new long[hours];
        for (int i = 0; i < hours; i++) {
            time[i] = timeArray.get(i).getAsLong();
        }

        // Member columns per model, ordered by member number; the unnumbered column sorts first.
        Map<String, Map<Integer, double[]>> byModel = new TreeMap<>();
        for (Map.Entry<String, JsonElement> column : hourly.entrySet()) {
            Matcher matcher = PRECIPITATION.matcher(column.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int member = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
            double[] values = millimetres(column.getValue().getAsJsonArray(), hours);
            Map<Integer, double[]> members = byModel.get(matcher.group(2));
            if (members == null) {
                members = new TreeMap<>();
                byModel.put(matcher.group(2), members);
            }
            members.put(member, values);
        }

        List<EnsemblePrecipitation.Model> models = new ArrayList<>();
        for (KnownModel known : MODELS) {
            Map<Integer, double[]> members = byModel.get(known.suffix);
            if (members != null) {
                models.add(new EnsemblePrecipitation.Model(known.name, known.blockHours,
                        members.values().toArray(new double[0][])));
            }
        }
        if (models.isEmpty()) {
            return null;
        }

        boolean[] isDay = isDay(hourly, hours);
        if (isDay == null) {
            return null;
        }
        return new EnsemblePrecipitation(time, models, isDay, fetchedAtMillis);
    }

    private static double[] millimetres(JsonArray array, int hours) {
        if (array.size() != hours) {
            throw new IllegalArgumentException("series length differs from time");
        }
        double[] values = new double[hours];
        for (int i = 0; i < hours; i++) {
            JsonElement value = array.get(i);
            values[i] = value.isJsonNull() ? Double.NaN : value.getAsDouble();
        }
        return values;
    }

    /**
     * Daylight per hour, from ICON's column when present, otherwise any model's: daylight does not
     * depend on the model. A missing value is read as night, so it can only withhold a "best".
     */
    @Nullable
    private static boolean[] isDay(JsonObject hourly, int hours) {
        JsonArray array = array(hourly, IS_DAY_PREFIX + MODELS.get(0).suffix);
        if (array == null) {
            for (Map.Entry<String, JsonElement> column : hourly.entrySet()) {
                if (column.getKey().startsWith(IS_DAY_PREFIX)
                        && column.getValue().isJsonArray()) {
                    array = column.getValue().getAsJsonArray();
                    break;
                }
            }
        }
        if (array == null || array.size() != hours) {
            return null;
        }
        boolean[] isDay = new boolean[hours];
        for (int i = 0; i < hours; i++) {
            JsonElement value = array.get(i);
            isDay[i] = !value.isJsonNull() && value.getAsInt() == 1;
        }
        return isDay;
    }

    @Nullable
    private static JsonArray array(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}
