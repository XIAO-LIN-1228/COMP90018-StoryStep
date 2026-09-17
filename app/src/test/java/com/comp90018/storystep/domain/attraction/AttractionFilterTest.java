package com.comp90018.storystep.domain.attraction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.attraction.AttractionFilter.Venue;
import com.comp90018.storystep.domain.model.IndoorClassification;

import org.junit.Test;

public class AttractionFilterTest {

    @Test
    public void noFilterMatchesEverything() {
        AttractionFilter filter = AttractionFilter.NONE;
        assertTrue(filter.matches("Old Arts", IndoorClassification.OUTDOOR, false));
        assertTrue(filter.matches("Museum", IndoorClassification.INDOOR, true));
        assertTrue(filter.matches("Unknown", IndoorClassification.UNKNOWN, false));
        assertFalse(filter.isActive());
    }

    @Test
    public void indoorFilterExcludesOutdoorAndUnknown() {
        AttractionFilter filter = AttractionFilter.NONE.withVenue(Venue.INDOOR);
        assertTrue(filter.matches("Museum", IndoorClassification.INDOOR, false));
        assertFalse(filter.matches("Garden", IndoorClassification.OUTDOOR, false));
        // A place the provider never described is not "indoor" and not "outdoor".
        assertFalse(filter.matches("Somewhere", IndoorClassification.UNKNOWN, false));
    }

    @Test
    public void outdoorFilterExcludesIndoorAndUnknown() {
        AttractionFilter filter = AttractionFilter.NONE.withVenue(Venue.OUTDOOR);
        assertTrue(filter.matches("Garden", IndoorClassification.OUTDOOR, false));
        assertFalse(filter.matches("Museum", IndoorClassification.INDOOR, false));
        assertFalse(filter.matches("Somewhere", IndoorClassification.UNKNOWN, false));
    }

    @Test
    public void unvisitedOnlyHidesVisitedPlaces() {
        AttractionFilter filter = AttractionFilter.NONE.withUnvisitedOnly(true);
        assertTrue(filter.matches("Garden", IndoorClassification.OUTDOOR, false));
        assertFalse(filter.matches("Garden", IndoorClassification.OUTDOOR, true));
    }

    @Test
    public void queryIsCaseInsensitiveSubstringOnName() {
        AttractionFilter filter = AttractionFilter.NONE.withQuery("  arts ");
        assertTrue(filter.matches("Old Arts Building", IndoorClassification.OUTDOOR, false));
        assertFalse(filter.matches("System Garden", IndoorClassification.OUTDOOR, false));
        assertTrue(filter.isActive());
        assertEquals("arts", filter.getQuery());
    }

    @Test
    public void filtersCombine() {
        AttractionFilter filter = AttractionFilter.NONE
                .withVenue(Venue.OUTDOOR)
                .withUnvisitedOnly(true)
                .withQuery("garden");
        assertTrue(filter.matches("System Garden", IndoorClassification.OUTDOOR, false));
        assertFalse(filter.matches("System Garden", IndoorClassification.OUTDOOR, true));
        assertFalse(filter.matches("System Garden", IndoorClassification.INDOOR, false));
        assertFalse(filter.matches("Old Arts", IndoorClassification.OUTDOOR, false));
    }

    @Test
    public void nullNameNeverMatchesAQuery() {
        assertFalse(AttractionFilter.NONE.withQuery("x").matches(null, IndoorClassification.INDOOR, false));
        assertTrue(AttractionFilter.NONE.matches(null, IndoorClassification.INDOOR, false));
    }
}
