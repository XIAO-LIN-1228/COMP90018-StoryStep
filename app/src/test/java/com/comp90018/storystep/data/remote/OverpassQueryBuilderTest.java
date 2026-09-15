package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

import java.util.Locale;

/**
 * Tests the generated Overpass QL text directly.
 *
 * <p>Worth asserting on the string because Overpass QL is a language: a wrong selector produces a
 * valid request that quietly returns the wrong places, which no integration test would flag as a
 * failure. A mistake here is only visible in the query itself.
 */
public class OverpassQueryBuilderTest {

    private static final GeoPoint MELBOURNE = new GeoPoint(-37.7950, 144.9650);

    @Test
    public void producesTheDocumentedQueryShape() {
        String query = OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 3800.0, 60);

        assertEquals("[out:json][timeout:25];\n"
                        + "(\n"
                        + "  nwr[\"tourism\"~\"^(museum|gallery|artwork|attraction|viewpoint)$\"]"
                        + "(around:3800,-37.795000,144.965000);\n"
                        + "  nwr[\"historic\"](around:3800,-37.795000,144.965000);\n"
                        + "  nwr[\"leisure\"~\"^(park|garden)$\"](around:3800,-37.795000,144.965000);\n"
                        + "  nwr[\"amenity\"~\"^(theatre|arts_centre)$\"]"
                        + "(around:3800,-37.795000,144.965000);\n"
                        + ");\n"
                        + "out center 60;",
                query);
    }

    @Test
    public void asksForNodesWaysAndRelationsTogether() {
        // The same landmark is tagged as any of the three depending on who mapped it; asking only
        // for nodes would miss every museum someone drew as a building outline.
        String query = OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 1000.0, 10);

        for (String line : query.split("\n")) {
            if (line.trim().startsWith("nwr") || line.trim().startsWith("node")) {
                assertTrue("every selector must be nwr: " + line, line.trim().startsWith("nwr["));
            }
        }
    }

    @Test
    public void asksForCentresSoWaysAndRelationsCarryACoordinate() {
        assertTrue(OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 1000.0, 10)
                .contains("out center "));
    }

    @Test
    public void theLimitIsTheResultBoundOverpassUnderstands() {
        assertTrue(OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 1000.0, 25)
                .endsWith("out center 25;"));
    }

    @Test
    public void coordinatesAreFormattedIndependentlyOfLocale() {
        // A locale that writes decimals with a comma would produce "-37,795000" and an Overpass
        // parse error, on some devices only.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertTrue(OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 1000.0, 10)
                    .contains("-37.795000,144.965000"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void theRadiusIsRoundedToWholeMetres() {
        assertTrue(OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 3799.6, 10)
                .contains("(around:3800,"));
    }

    @Test
    public void aNonPositiveRadiusIsRejectedRatherThanSentAsIs() {
        for (double radius : new double[]{0.0, -1.0, Double.POSITIVE_INFINITY}) {
            try {
                OverpassQueryBuilder.nearbyAttractions(MELBOURNE, radius, 10);
                fail("expected rejection of radius " + radius);
            } catch (IllegalArgumentException expected) {
                // The point: a bad radius must not become a query at all.
            }
        }
    }

    @Test
    public void aNonPositiveLimitIsRejected() {
        try {
            OverpassQueryBuilder.nearbyAttractions(MELBOURNE, 1000.0, 0);
            fail("expected rejection of a zero limit");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
