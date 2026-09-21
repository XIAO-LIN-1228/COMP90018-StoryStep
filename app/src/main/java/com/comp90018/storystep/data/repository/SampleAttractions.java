package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The bundled demonstration set.
 *
 * <p>Since REQ-18 these are no longer a stand-in for the attraction service — real places are
 * fetched from OpenStreetMap alongside them. They stay because they are the only content
 * guaranteed to be present at a cold offline start, and because they carry written stories, which
 * the provider almost never supplies.
 *
 * <p>Every record here is marked {@link com.comp90018.storystep.domain.model.AttractionProvenance#SEED}
 * and is never presented as a live result. A refresh replaces provider rows only, so this set
 * survives one.
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

    /**
     * Four of the five carry a photograph from Wikimedia Commons, the same source the fetched
     * places use, so a seed looks no different from a fetched place in the list. System Garden
     * has no photograph on Commons; it keeps the bundled artwork.
     */
    public static List<Attraction> get() {
        return Collections.unmodifiableList(Arrays.asList(
                Attraction.seeded(
                        "old-arts",
                        "Old Arts Building",
                        AttractionCategory.UNIVERSITY_LANDMARK,
                        "The 1924 clocktower building at the heart of the Parkville campus, and "
                                + "one of the oldest surviving teaching buildings of the "
                                + "University of Melbourne.",
                        IndoorClassification.OUTDOOR,
                        new GeoPoint(-37.79810, 144.95910))
                        .withImageUrl("https://commons.wikimedia.org/wiki/Special:FilePath/Old_Arts_Building_University_of_Melbourne_2018.jpg?width=1280"),
                Attraction.seeded(
                        "potter-museum",
                        "Ian Potter Museum of Art",
                        AttractionCategory.MUSEUM,
                        "The university art museum on Swanston Street, holding collections that "
                                + "range from antiquities to contemporary Australian art.",
                        IndoorClassification.INDOOR,
                        new GeoPoint(-37.79750, 144.96400))
                        .withImageUrl("https://commons.wikimedia.org/wiki/Special:FilePath/Ian_Potter_Museum_of_Art_2010.jpg?width=1280"),
                Attraction.seeded(
                        "system-garden",
                        "System Garden",
                        AttractionCategory.PARK,
                        "A small botanical garden laid out in 1856 for teaching plant "
                                + "classification, still arranged around its original tower.",
                        IndoorClassification.OUTDOOR,
                        new GeoPoint(-37.79720, 144.96050)),
                Attraction.seeded(
                        "royal-exhibition-building",
                        "Royal Exhibition Building",
                        AttractionCategory.HISTORICAL_BUILDING,
                        "Built for the 1880 International Exhibition and the first building in "
                                + "Australia to receive UNESCO World Heritage listing.",
                        IndoorClassification.INDOOR,
                        new GeoPoint(-37.80460, 144.97170))
                        .withImageUrl("https://commons.wikimedia.org/wiki/Special:FilePath/Royal_exhibition_building_tulips_straight.jpg?width=1280"),
                Attraction.seeded(
                        "melbourne-museum",
                        "Melbourne Museum",
                        AttractionCategory.MUSEUM,
                        "A natural and cultural history museum in Carlton Gardens, with galleries "
                                + "covering science, Victorian history and First Peoples stories.",
                        IndoorClassification.INDOOR,
                        new GeoPoint(-37.80330, 144.97170))
                        .withImageUrl("https://commons.wikimedia.org/wiki/Special:FilePath/Melbourne_museum_exterior_panorama.jpg?width=1280")));
    }
}
