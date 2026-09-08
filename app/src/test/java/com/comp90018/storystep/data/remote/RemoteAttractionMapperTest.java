package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.model.IndoorClassification;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The boundary tests for REQ-18.3: nothing the provider sends may reach the domain unchecked.
 *
 * <p>Grouped by the kind of doubt each case resolves — identity, position, classification,
 * content, and whether an answer is empty or broken.
 */
public class RemoteAttractionMapperTest {

    // --- identity ------------------------------------------------------------------------------

    @Test
    public void aNodeGetsAProviderQualifiedIdentifier() {
        Attraction attraction = RemoteAttractionMapper.toDomain(
                node(2412481861L, -37.8032, 144.9713, tags("name", "Melbourne Museum")));

        assertEquals("osm:node/2412481861", attraction.getId());
        assertEquals(AttractionProvenance.OPENSTREETMAP, attraction.getProvenance());
    }

    @Test
    public void aWayAndANodeWithTheSameNumberAreDifferentPlaces() {
        Attraction asNode = RemoteAttractionMapper.toDomain(
                node(26621235L, -37.8046, 144.9718, tags("name", "Somewhere")));
        Attraction asWay = RemoteAttractionMapper.toDomain(
                way(26621235L, -37.8046, 144.9718, tags("name", "Somewhere")));

        assertEquals("osm:node/26621235", asNode.getId());
        assertEquals("osm:way/26621235", asWay.getId());
    }

    @Test
    public void anElementOfAnUnrecognisedTypeIsRejected() {
        OverpassElementDto element = node(1L, -37.8, 144.9, tags("name", "Mystery"));
        element.type = "changeset";

        assertNull(RemoteAttractionMapper.toDomain(element));
    }

    @Test
    public void anElementWithNoIdIsRejected() {
        OverpassElementDto element = node(1L, -37.8, 144.9, tags("name", "Mystery"));
        element.id = null;

        assertNull(RemoteAttractionMapper.toDomain(element));
    }

    // --- position ------------------------------------------------------------------------------

    @Test
    public void aWayWithoutItsOwnCoordinateFallsBackToItsCentre() {
        OverpassElementDto element = way(26621235L, -37.8046, 144.9718,
                tags("name", "Royal Exhibition Building"));

        Attraction attraction = RemoteAttractionMapper.toDomain(element);

        assertEquals(-37.8046, attraction.getLocation().getLatitude(), 1e-9);
        assertEquals(144.9718, attraction.getLocation().getLongitude(), 1e-9);
    }

    @Test
    public void anElementWithNoCoordinateAtAllIsRejected() {
        OverpassElementDto element = new OverpassElementDto();
        element.type = "node";
        element.id = 1L;
        element.tags = tags("name", "Nowhere");

        assertNull(RemoteAttractionMapper.toDomain(element));
    }

    @Test
    public void anOutOfRangeCoordinateIsRejectedRatherThanClamped() {
        assertNull(RemoteAttractionMapper.toDomain(
                node(1L, -137.0, 144.9, tags("name", "Impossible"))));
    }

    @Test
    public void aNonFiniteCoordinateIsRejected() {
        assertNull(RemoteAttractionMapper.toDomain(
                node(1L, Double.NaN, 144.9, tags("name", "Impossible"))));
        assertNull(RemoteAttractionMapper.toDomain(
                node(2L, -37.8, Double.POSITIVE_INFINITY, tags("name", "Impossible"))));
    }

    // --- naming --------------------------------------------------------------------------------

    @Test
    public void anUnnamedPlaceIsRejectedBecauseItCannotBePresented() {
        assertNull(RemoteAttractionMapper.toDomain(
                node(1L, -37.8, 144.9, tags("tourism", "museum"))));
    }

    @Test
    public void aBlankNameIsTreatedAsNoName() {
        assertNull(RemoteAttractionMapper.toDomain(
                node(1L, -37.8, 144.9, tags("name", "   "))));
    }

    // --- category ------------------------------------------------------------------------------

    @Test
    public void tagsMapOntoTheApplicationsCategories() {
        assertEquals(AttractionCategory.MUSEUM, categoryOf(tags("name", "A", "tourism", "museum")));
        assertEquals(AttractionCategory.MUSEUM, categoryOf(tags("name", "A", "tourism", "gallery")));
        assertEquals(AttractionCategory.PUBLIC_ART,
                categoryOf(tags("name", "A", "tourism", "artwork")));
        assertEquals(AttractionCategory.PUBLIC_ART,
                categoryOf(tags("name", "A", "historic", "memorial")));
        assertEquals(AttractionCategory.HISTORICAL_BUILDING,
                categoryOf(tags("name", "A", "historic", "building")));
        assertEquals(AttractionCategory.PARK, categoryOf(tags("name", "A", "leisure", "park")));
        assertEquals(AttractionCategory.PARK, categoryOf(tags("name", "A", "leisure", "garden")));
        assertEquals(AttractionCategory.PARK,
                categoryOf(tags("name", "A", "tourism", "viewpoint")));
    }

    @Test
    public void anUnrecognisedTaggingFallsBackInsteadOfFailing() {
        assertEquals(AttractionCategory.CULTURAL_SITE,
                categoryOf(tags("name", "A", "amenity", "theatre")));
    }

    @Test
    public void theHistoricReadingWinsOverTheGenericAttractionOne() {
        assertEquals(AttractionCategory.HISTORICAL_BUILDING,
                categoryOf(tags("name", "Royal Exhibition Building",
                        "tourism", "attraction", "historic", "building")));
    }

    @Test
    public void aUniversityLandmarkIsNeverInferred() {
        // OSM has no dependable tag for it, so the mapper must not produce one from a guess.
        for (String amenity : Arrays.asList("university", "college", "school")) {
            assertFalse(AttractionCategory.UNIVERSITY_LANDMARK
                    == categoryOf(tags("name", "A", "amenity", amenity)));
        }
    }

    // --- indoor classification -----------------------------------------------------------------

    @Test
    public void aBuildingIsIndoorAndAParkIsOutdoor() {
        assertEquals(IndoorClassification.INDOOR,
                indoorOf(tags("name", "A", "tourism", "museum")));
        assertEquals(IndoorClassification.INDOOR,
                indoorOf(tags("name", "A", "building", "yes")));
        assertEquals(IndoorClassification.OUTDOOR,
                indoorOf(tags("name", "A", "leisure", "park")));
        assertEquals(IndoorClassification.OUTDOOR,
                indoorOf(tags("name", "A", "historic", "memorial")));
    }

    @Test
    public void aPlaceOsmSaysNothingAboutStaysUnknown() {
        // The case this tri-state exists for: silence is not a claim that a place is outdoors.
        assertEquals(IndoorClassification.UNKNOWN,
                indoorOf(tags("name", "A", "tourism", "attraction")));
    }

    @Test
    public void aParkWithATaggedBuildingIsStillAPark() {
        // A pavilion inside a garden is tagged building=yes; what a visitor experiences is the
        // garden, so the outdoor reading has to be tested first.
        assertEquals(IndoorClassification.OUTDOOR,
                indoorOf(tags("name", "A", "leisure", "garden", "building", "yes")));
    }

    // --- content and references ----------------------------------------------------------------

    @Test
    public void aDescriptionIsUsedWhenOsmHasOne() {
        Attraction attraction = RemoteAttractionMapper.toDomain(node(1L, -37.8, 144.9,
                tags("name", "A", "description", "A tower built in 1856.")));

        assertEquals("A tower built in 1856.", attraction.getDescription());
        assertTrue(attraction.hasDescription());
    }

    @Test
    public void anInscriptionStandsInWhenThereIsNoDescription() {
        Attraction attraction = RemoteAttractionMapper.toDomain(node(1L, -37.8, 144.9,
                tags("name", "A", "inscription", "Erected by public subscription, 1901.")));

        assertEquals("Erected by public subscription, 1901.", attraction.getDescription());
    }

    @Test
    public void aPlaceWithNoTextGetsAnEmptyDescriptionRatherThanAnInventedOne() {
        Attraction attraction = RemoteAttractionMapper.toDomain(
                node(1L, -37.8, 144.9, tags("name", "A", "tourism", "museum")));

        assertEquals("", attraction.getDescription());
        assertFalse(attraction.hasDescription());
    }

    @Test
    public void wikidataAndWikipediaTagsArePreservedForLaterEnrichment() {
        assertEquals("wikidata:Q1747074", RemoteAttractionMapper.toDomain(node(1L, -37.8, 144.9,
                tags("name", "A", "wikidata", "Q1747074"))).getSourceRef());
        assertEquals("wikipedia:en:System Garden",
                RemoteAttractionMapper.toDomain(node(2L, -37.8, 144.9,
                        tags("name", "A", "wikipedia", "en:System Garden"))).getSourceRef());
        assertNull(RemoteAttractionMapper.toDomain(node(3L, -37.8, 144.9,
                tags("name", "A"))).getSourceRef());
    }

    // --- whole responses -----------------------------------------------------------------------

    @Test
    public void anEmptyElementsArrayIsAValidEmptyAnswer() {
        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(
                response(new ArrayList<>()));

        assertFalse("an empty area is an answer, not a fault", mapped.isMalformed());
        assertTrue(mapped.getAttractions().isEmpty());
    }

    @Test
    public void aResponseWithNoElementsFieldAtAllIsMalformed() {
        assertTrue(RemoteAttractionMapper.toDomain(new OverpassResponseDto()).isMalformed());
        assertTrue(RemoteAttractionMapper.toDomain((OverpassResponseDto) null).isMalformed());
    }

    @Test
    public void aMixedResponseKeepsTheUsableRecordsAndDropsTheRest() {
        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(response(Arrays.asList(
                node(1L, -37.8032, 144.9713, tags("name", "Melbourne Museum", "tourism", "museum")),
                node(2L, -37.8, 144.9, tags("tourism", "museum")),
                node(3L, Double.NaN, 144.9, tags("name", "Broken")))));

        assertFalse(mapped.isMalformed());
        assertEquals(1, mapped.getAttractions().size());
        assertEquals("osm:node/1", mapped.getAttractions().get(0).getId());
    }

    @Test
    public void aResponseWhoseRecordsAreAllUnusableIsMalformedRatherThanEmpty() {
        // Caching this as "no attractions here" would blank the area for a day on the strength of
        // a response that was never usable in the first place.
        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(response(Arrays.asList(
                node(1L, -37.8, 144.9, tags("tourism", "museum")),
                node(2L, Double.NaN, 144.9, tags("name", "Broken")))));

        assertTrue(mapped.isMalformed());
        assertTrue(mapped.getAttractions().isEmpty());
    }

    // --- deduplication -------------------------------------------------------------------------

    @Test
    public void theSameIdentifierTwiceProducesOneRecord() {
        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(response(Arrays.asList(
                node(1L, -37.8032, 144.9713, tags("name", "Melbourne Museum")),
                node(1L, -37.8032, 144.9713, tags("name", "Melbourne Museum")))));

        assertEquals(1, mapped.getAttractions().size());
    }

    @Test
    public void aNodeAndAWayForOnePlaceCollapseToTheArea() {
        // OSM routinely holds both a marker and a building outline for one museum.
        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(response(Arrays.asList(
                node(1L, -37.80320, 144.97170, tags("name", "Melbourne Museum")),
                way(2L, -37.80325, 144.97175, tags("name", "Melbourne Museum")))));

        assertEquals(1, mapped.getAttractions().size());
        assertEquals("the area is the better record", "osm:way/2",
                mapped.getAttractions().get(0).getId());
    }

    @Test
    public void twoDistinctPlacesSharingANameAreBothKept() {
        // Same name, different suburbs: "Memorial Park" is not rare, and merging them would hide
        // one of two real places.
        RemoteAttractionMapper.Mapped mapped = RemoteAttractionMapper.toDomain(response(Arrays.asList(
                node(1L, -37.8000, 144.9600, tags("name", "Memorial Park")),
                node(2L, -37.8500, 145.0500, tags("name", "Memorial Park")))));

        assertEquals(2, mapped.getAttractions().size());
    }

    // --- helpers -------------------------------------------------------------------------------

    private static AttractionCategory categoryOf(Map<String, String> tags) {
        return RemoteAttractionMapper.toDomain(node(1L, -37.8, 144.9, tags)).getCategory();
    }

    private static IndoorClassification indoorOf(Map<String, String> tags) {
        return RemoteAttractionMapper.toDomain(node(1L, -37.8, 144.9, tags)).getIndoor();
    }

    private static OverpassElementDto node(long id, double latitude, double longitude,
                                           Map<String, String> tags) {
        OverpassElementDto element = new OverpassElementDto();
        element.type = "node";
        element.id = id;
        element.lat = latitude;
        element.lon = longitude;
        element.tags = tags;
        return element;
    }

    private static OverpassElementDto way(long id, double latitude, double longitude,
                                          Map<String, String> tags) {
        OverpassElementDto element = new OverpassElementDto();
        element.type = "way";
        element.id = id;
        element.center = new OverpassElementDto.Center();
        element.center.lat = latitude;
        element.center.lon = longitude;
        element.tags = tags;
        return element;
    }

    private static OverpassResponseDto response(List<OverpassElementDto> elements) {
        OverpassResponseDto dto = new OverpassResponseDto();
        dto.elements = elements;
        return dto;
    }

    private static Map<String, String> tags(String... keyValuePairs) {
        Map<String, String> tags = new LinkedHashMap<>();
        for (int index = 0; index < keyValuePairs.length; index += 2) {
            tags.put(keyValuePairs[index], keyValuePairs[index + 1]);
        }
        return tags;
    }
}
