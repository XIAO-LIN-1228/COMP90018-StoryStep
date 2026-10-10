package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.forecast.SunTimes;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;

/** Reading recorded Open-Meteo answers, and refusing broken ones. */
public class RemoteForecastMapperTest {

    @Test
    public void theRecordedEnsembleHoldsThreeModelsAtTheirNativeSteps() {
        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(
                ForecastFixtures.ensemble("singapore"), ForecastFixtures.RECORDED_AT);

        assertNotNull(ensemble);
        assertEquals(34, ensemble.hourCount());
        assertEquals(ForecastFixtures.FIRST_END_SECOND, ensemble.endEpochSecond(0));
        assertEquals(3, ensemble.getModels().size());
        EnsemblePrecipitation.Model icon = ensemble.getModels().get(0);
        EnsemblePrecipitation.Model ecmwf = ensemble.getModels().get(1);
        EnsemblePrecipitation.Model gfs = ensemble.getModels().get(2);
        assertEquals(Arrays.asList("ICON", 1, 40), Arrays.asList(icon.getName(),
                icon.getBlockHours(), icon.memberCount()));
        assertEquals(Arrays.asList("ECMWF", 3, 51), Arrays.asList(ecmwf.getName(),
                ecmwf.getBlockHours(), ecmwf.memberCount()));
        assertEquals(Arrays.asList("GFS", 3, 31), Arrays.asList(gfs.getName(),
                gfs.getBlockHours(), gfs.memberCount()));
        assertEquals(ForecastFixtures.RECORDED_AT, ensemble.getFetchedAtMillis());
    }

    @Test
    public void membersKeepTheirOrderAndValues() {
        JsonObject body = ForecastFixtures.ensemble("singapore");
        JsonArray member07 = body.getAsJsonObject("hourly")
                .getAsJsonArray("precipitation_member07_ncep_gefs025");

        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(body, 0L);

        // Index 0 is the unnumbered column, so member 07 sits at index 7.
        EnsemblePrecipitation.Model gfs = ensemble.getModels().get(2);
        for (int h = 0; h < ensemble.hourCount(); h++) {
            assertEquals(member07.get(h).getAsDouble(), gfs.millimetres(7, h), 0.0);
        }
    }

    @Test
    public void threeHourModelsRepeatTheirBlockValueInTheRecordedData() {
        // The assumption the block rule rests on: rows ending 01:00, 02:00 and 03:00 UTC match.
        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(
                ForecastFixtures.ensemble("singapore"), 0L);
        // The first row ending at 01:00, 04:00, ... UTC starts a block.
        int first = 0;
        while ((ensemble.endEpochSecond(first) / 3_600L) % 3 != 1) {
            first++;
        }
        for (EnsemblePrecipitation.Model model : ensemble.getModels().subList(1, 3)) {
            for (int m = 0; m < model.memberCount(); m++) {
                for (int b = first; b + 2 < ensemble.hourCount(); b += 3) {
                    assertEquals(model.millimetres(m, b), model.millimetres(m, b + 1), 0.0);
                    assertEquals(model.millimetres(m, b), model.millimetres(m, b + 2), 0.0);
                }
            }
        }
    }

    @Test
    public void daylightIsRead() {
        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(
                ForecastFixtures.ensemble("melbourne"), 0L);

        // Row 0 ends 00:00 UTC, 11:00 in Melbourne; row 12 ends 12:00 UTC, 23:00 there.
        SunTimes sun = ensemble.getPlace().getSun();
        assertEquals(Boolean.TRUE, sun.isDaylight(ensemble.endEpochSecond(0) * 1000L));
        assertEquals(Boolean.FALSE, sun.isDaylight(ensemble.endEpochSecond(12) * 1000L));
        assertEquals("Australia/Melbourne", ensemble.getPlace().getTimezoneId());
        assertEquals(39_600, ensemble.getPlace().getUtcOffsetSeconds());
    }

    @Test
    public void aMissingValueBecomesNaN() {
        JsonObject body = ForecastFixtures.ensemble("melbourne");
        body.getAsJsonObject("hourly").getAsJsonArray("precipitation_member03_icon_seamless_eps")
                .set(5, JsonNull.INSTANCE);

        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(body, 0L);

        assertTrue(Double.isNaN(ensemble.getModels().get(0).millimetres(3, 5)));
    }

    @Test
    public void aModelThatIsAbsentIsSimplyNotThere() {
        JsonObject body = ForecastFixtures.ensemble("melbourne");
        JsonObject hourly = body.getAsJsonObject("hourly");
        for (String key : hourly.keySet().toArray(new String[0])) {
            if (key.endsWith("_ecmwf_ifs025_ensemble")) {
                hourly.remove(key);
            }
        }

        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(body, 0L);

        assertEquals(2, ensemble.getModels().size());
        assertEquals("GFS", ensemble.getModels().get(1).getName());
    }

    @Test
    public void unknownModelsAreIgnored() {
        JsonObject body = JsonParser.parseString("{\"hourly\":{"
                + "\"time\":[1791586800,1791590400],"
                + "\"precipitation_member01_other\":[0.0,0.0],"
                + "\"x\":0},\"daily\":{\"sunrise_other\":[1791574975],"
                + "\"sunset_other\":[1791621146]}}").getAsJsonObject();

        assertNull(RemoteForecastMapper.toEnsemble(body, 0L));
    }

    @Test
    public void aSeriesOfTheWrongLengthRejectsTheResponse() {
        JsonObject body = ForecastFixtures.ensemble("melbourne");
        body.getAsJsonObject("hourly").getAsJsonArray("precipitation_member03_icon_seamless_eps")
                .remove(0);

        assertNull(RemoteForecastMapper.toEnsemble(body, 0L));
    }

    @Test
    public void noSunTimesRejectsTheResponse() {
        JsonObject body = ForecastFixtures.ensemble("melbourne");
        body.remove("daily");

        assertNull(RemoteForecastMapper.toEnsemble(body, 0L));
    }

    @Test
    public void anErrorBodyOrNothingIsRejected() {
        assertNull(RemoteForecastMapper.toEnsemble(null, 0L));
        assertNull(RemoteForecastMapper.toEnsemble(JsonParser.parseString(
                "{\"error\":true,\"reason\":\"Invalid value\"}").getAsJsonObject(), 0L));
        assertNull(RemoteForecastMapper.toEnsemble(JsonParser.parseString(
                "{\"hourly\":{\"time\":[1791586800,1791594000],"
                        + "\"precipitation_member01_icon_seamless_eps\":[0,0],"
                        + "\"x\":0},\"daily\":{\"sunrise\":[1791574975],"
                        + "\"sunset\":[1791621146]}}").getAsJsonObject(), 0L));
    }

    @Test
    public void theRecordedHourlyForecastIsRead() {
        HourlyForecast forecast = RemoteForecastMapper.toHourly(
                ForecastFixtures.hourly("singapore"), ForecastFixtures.RECORDED_AT);

        assertNotNull(forecast);
        assertEquals(26, forecast.getHours().size());
        HourlyForecast.Hour first = forecast.getHours().get(0);
        assertEquals(ForecastFixtures.FIRST_END_SECOND, first.getEndEpochSecond());
        assertNotNull(first.getPrecipitationProbability());
        assertNotNull(first.getCondition());
    }

    @Test
    public void weatherCodesUseTheExistingConditionMapping() {
        HourlyForecastDto dto = dto("[1791586800,1791590400]", "[10,90]", "[0,61]");

        HourlyForecast forecast = RemoteForecastMapper.toHourly(dto, 0L);

        assertEquals(WeatherCondition.CLEAR, forecast.getHours().get(0).getCondition());
        assertEquals(WeatherCondition.RAIN, forecast.getHours().get(1).getCondition());
    }

    @Test
    public void eachHourIsDayOrNightByItsMiddleAgainstSunriseAndSunset() {
        // Sunset 19:32:26 local (08:32:26 UTC). The 08:00-09:00 UTC hour's middle, 08:30, is
        // before sunset; the 09:00-10:00 hour is after.
        HourlyForecastDto dto = new com.google.gson.Gson().fromJson("{\"hourly\":{"
                + "\"time\":[1791622800,1791626400],\"precipitation_probability\":[0,0],"
                + "\"weather_code\":[0,0]},\"timezone\":\"Australia/Melbourne\","
                + "\"utc_offset_seconds\":39600,\"daily\":{\"sunrise\":[1791574975],"
                + "\"sunset\":[1791621146]}}", HourlyForecastDto.class);

        HourlyForecast forecast = RemoteForecastMapper.toHourly(dto, 0L);

        assertEquals(Boolean.TRUE, forecast.getHours().get(0).getDaylight());
        assertEquals(Boolean.FALSE, forecast.getHours().get(1).getDaylight());
        assertEquals("Australia/Melbourne", forecast.getPlace().getTimezoneId());
        assertNull(RemoteForecastMapper.toHourly(dto("[1791586800]", "[0]", "[0]"), 0L)
                .getHours().get(0).getDaylight());
    }

    @Test
    public void missingHourlyValuesStayMissing() {
        HourlyForecast forecast = RemoteForecastMapper.toHourly(
                dto("[1791586800]", "[null]", "[null]"), 0L);

        assertNull(forecast.getHours().get(0).getPrecipitationProbability());
        assertNull(forecast.getHours().get(0).getCondition());
    }

    @Test
    public void brokenHourlyResponsesAreRejected() {
        assertNull(RemoteForecastMapper.toHourly(null, 0L));
        assertNull(RemoteForecastMapper.toHourly(new HourlyForecastDto(), 0L));
        assertNull(RemoteForecastMapper.toHourly(dto("[]", "[]", "[]"), 0L));
        assertNull(RemoteForecastMapper.toHourly(dto("[1791586800,1791590400]", "[1]", "[0,0]"), 0L));
        assertNull(RemoteForecastMapper.toHourly(dto("[1791586800,1791594000]", "[1,1]", "[0,0]"), 0L));
        assertNull(RemoteForecastMapper.toHourly(dto("[1791586800]", "[150]", "[0]"), 0L));
    }

    private static HourlyForecastDto dto(String time, String probability, String codes) {
        return new com.google.gson.Gson().fromJson("{\"hourly\":{\"time\":" + time
                + ",\"precipitation_probability\":" + probability
                + ",\"weather_code\":" + codes + "}}", HourlyForecastDto.class);
    }
}
