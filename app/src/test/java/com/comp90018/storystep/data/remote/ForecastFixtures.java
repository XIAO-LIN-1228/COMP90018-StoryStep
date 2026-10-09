package com.comp90018.storystep.data.remote;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Open-Meteo answers recorded at 2026-10-09 23:12:39 UTC (10:12 in Melbourne), for Melbourne
 * (-37.825, 144.975), dry, and Singapore (1.325, 103.825), showery. Kept under
 * {@code src/test/resources/forecast}.
 */
final class ForecastFixtures {

    /** When the fixtures were requested. */
    static final long RECORDED_AT = 1_791_587_559_000L;

    /** First timestamp in every fixture: 23:00 UTC, the end of the 22:00-23:00 hour. */
    static final long FIRST_END_SECOND = 1_791_586_800L;

    private ForecastFixtures() {
    }

    static String text(String name) {
        try (InputStream in = ForecastFixtures.class.getClassLoader()
                .getResourceAsStream("forecast/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonObject ensemble(String place) {
        return JsonParser.parseString(text("ensemble_" + place + ".json")).getAsJsonObject();
    }

    static HourlyForecastDto hourly(String place) {
        return new Gson().fromJson(text("hourly_" + place + ".json"), HourlyForecastDto.class);
    }
}
