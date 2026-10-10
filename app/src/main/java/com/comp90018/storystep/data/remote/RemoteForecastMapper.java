package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.forecast.SkyCondition;
import com.comp90018.storystep.domain.forecast.SunTimes;
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
        List<Double> gusts = dto.hourly.windGusts;
        List<Double> winds = dto.hourly.windSpeed;
        List<Double> feels = dto.hourly.apparentTemperature;
        if ((probability != null && probability.size() != time.size())
                || (codes != null && codes.size() != time.size())
                || (gusts != null && gusts.size() != time.size())
                || (winds != null && winds.size() != time.size())
                || (feels != null && feels.size() != time.size())) {
            return null;
        }
        ForecastPlace place = place(dto);
        List<HourlyForecast.Hour> hours = new ArrayList<>(time.size());
        for (int i = 0; i < time.size(); i++) {
            Long end = time.get(i);
            if (end == null) {
                return null;
            }
            Integer code = codes == null ? null : codes.get(i);
            // Day or night by the middle of the hour, against that day's sunrise and sunset.
            long middleMillis = end * 1000L - 30L * 60L * 1000L;
            hours.add(new HourlyForecast.Hour(end,
                    probability == null ? null : probability.get(i),
                    code == null ? null : SkyCondition.fromWmo(code),
                    place.getSun().isDaylight(middleMillis), code,
                    gusts == null ? null : gusts.get(i),
                    winds == null ? null : winds.get(i),
                    feels == null ? null : feels.get(i)));
        }
        try {
            return new HourlyForecast(hours, fetchedAtMillis, place);
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

        SunTimes sun = ensembleSun(body);
        if (!sun.isKnown()) {
            // Without sunrise and sunset every walk would read as after dark.
            return null;
        }
        return new EnsemblePrecipitation(time, models, new ForecastPlace(
                string(body, "timezone"), integer(body, "utc_offset_seconds"), sun),
                fetchedAtMillis);
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

    /** Timezone and sun times of the hourly response; unknown parts stay unknown. */
    private static ForecastPlace place(HourlyForecastDto dto) {
        SunTimes sun = SunTimes.UNKNOWN;
        if (dto.daily != null) {
            sun = sunTimes(dto.daily.sunrise, dto.daily.sunset);
        }
        return new ForecastPlace(dto.timezone,
                dto.utcOffsetSeconds == null ? 0 : dto.utcOffsetSeconds, sun);
    }

    /**
     * Sunrise and sunset from the ensemble's daily block. With several models each column carries
     * the model's suffix and they differ by seconds (different grid points); ICON's is preferred,
     * then any model's, then unsuffixed columns.
     */
    private static SunTimes ensembleSun(JsonObject body) {
        JsonElement dailyElement = body.get("daily");
        if (dailyElement == null || !dailyElement.isJsonObject()) {
            return SunTimes.UNKNOWN;
        }
        JsonObject daily = dailyElement.getAsJsonObject();
        List<String> suffixes = new ArrayList<>();
        for (KnownModel known : MODELS) {
            suffixes.add("_" + known.suffix);
        }
        suffixes.add("");
        for (String suffix : suffixes) {
            SunTimes sun = sunTimes(longs(array(daily, "sunrise" + suffix)),
                    longs(array(daily, "sunset" + suffix)));
            if (sun.isKnown()) {
                return sun;
            }
        }
        return SunTimes.UNKNOWN;
    }

    /** Sun times from two parallel lists; unknown when either is missing, short or out of order. */
    private static SunTimes sunTimes(@Nullable List<Long> sunrise, @Nullable List<Long> sunset) {
        if (sunrise == null || sunset == null || sunrise.isEmpty()
                || sunrise.size() != sunset.size()) {
            return SunTimes.UNKNOWN;
        }
        long[] rise = new long[sunrise.size()];
        long[] set = new long[sunset.size()];
        for (int i = 0; i < rise.length; i++) {
            if (sunrise.get(i) == null || sunset.get(i) == null) {
                return SunTimes.UNKNOWN;
            }
            rise[i] = sunrise.get(i);
            set[i] = sunset.get(i);
        }
        try {
            return SunTimes.of(rise, set);
        } catch (IllegalArgumentException outOfOrder) {
            // Polar day or night, or a broken answer: daylight is simply not known.
            return SunTimes.UNKNOWN;
        }
    }

    @Nullable
    private static List<Long> longs(@Nullable JsonArray array) {
        if (array == null) {
            return null;
        }
        List<Long> values = new ArrayList<>(array.size());
        for (JsonElement value : array) {
            values.add(value.isJsonNull() ? null : value.getAsLong());
        }
        return values;
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static int integer(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    @Nullable
    private static JsonArray array(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }
}
