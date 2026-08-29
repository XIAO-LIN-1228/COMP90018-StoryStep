package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fixed demonstration data for Stage 1.
 *
 * <p>These entries stand in for the attraction service until the remote source is implemented.
 * They are real public places, so the coordinates stay meaningful once real location data is
 * wired up, but nothing here is fetched from a network or a device sensor.
 */
public final class SampleAttractions {

    /**
     * Reference position used while no exploration session is running: the University of
     * Melbourne Parkville campus. Stage 2 replaces this with the session's real fix; until then
     * the listed distances are explicitly approximate demonstration values.
     */
    public static final GeoPoint DEMO_ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private SampleAttractions() {
        // Utility class.
    }

    public static List<Attraction> get() {
        return Collections.unmodifiableList(Arrays.asList(
                new Attraction(
                        "old-arts",
                        "Old Arts Building",
                        AttractionCategory.UNIVERSITY_LANDMARK,
                        "The 1924 clocktower building at the heart of the Parkville campus, and "
                                + "one of the oldest surviving teaching buildings of the "
                                + "University of Melbourne.",
                        false,
                        new GeoPoint(-37.79810, 144.95910)),
                new Attraction(
                        "potter-museum",
                        "Ian Potter Museum of Art",
                        AttractionCategory.MUSEUM,
                        "The university art museum on Swanston Street, holding collections that "
                                + "range from antiquities to contemporary Australian art.",
                        true,
                        new GeoPoint(-37.79750, 144.96400)),
                new Attraction(
                        "system-garden",
                        "System Garden",
                        AttractionCategory.PARK,
                        "A small botanical garden laid out in 1856 for teaching plant "
                                + "classification, still arranged around its original tower.",
                        false,
                        new GeoPoint(-37.79720, 144.96050)),
                new Attraction(
                        "royal-exhibition-building",
                        "Royal Exhibition Building",
                        AttractionCategory.HISTORICAL_BUILDING,
                        "Built for the 1880 International Exhibition and the first building in "
                                + "Australia to receive UNESCO World Heritage listing.",
                        true,
                        new GeoPoint(-37.80460, 144.97170)),
                new Attraction(
                        "melbourne-museum",
                        "Melbourne Museum",
                        AttractionCategory.MUSEUM,
                        "A natural and cultural history museum in Carlton Gardens, with galleries "
                                + "covering science, Victorian history and First Peoples stories.",
                        true,
                        new GeoPoint(-37.80330, 144.97170))));
    }
}
