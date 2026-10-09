package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
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
        for (EnsemblePrecipitation.Model model : ensemble.getModels().subList(1, 3)) {
            for (int m = 0; m < model.memberCount(); m++) {
                // Row 0 ends 23:00; rows 2-4 end 01:00-03:00.
                assertEquals(model.millimetres(m, 2), model.millimetres(m, 3), 0.0);
                assertEquals(model.millimetres(m, 2), model.millimetres(m, 4), 0.0);
            }
        }
    }

    @Test
    public void daylightIsRead() {
        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(
                ForecastFixtures.ensemble("melbourne"), 0L);

        // Row 0 ends 23:00 UTC, 10:00 in Melbourne; row 12 ends 11:00 UTC, 22:00 there.
        assertTrue(ensemble.isDay(0));
        assertFalse(ensemble.isDay(12));
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
                + "\"is_day_other\":[1,1]}}").getAsJsonObject();

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
    public void noDaylightColumnRejectsTheResponse() {
        JsonObject body = ForecastFixtures.ensemble("melbourne");
        JsonObject hourly = body.getAsJsonObject("hourly");
        for (String key : hourly.keySet().toArray(new String[0])) {
            if (key.startsWith("is_day")) {
                hourly.remove(key);
            }
        }

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
                        + "\"is_day_icon_seamless_eps\":[1,1]}}").getAsJsonObject(), 0L));
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
