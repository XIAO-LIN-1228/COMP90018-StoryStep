package com.comp90018.storystep.ui;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.AttractionCategory;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Every place must end up with an image. A category added later that nobody wires here would
 * otherwise fall through to whatever the default happens to be, which is exactly the silent
 * gap this artwork exists to close.
 */
public class AttractionArtworkTest {

    @Test
    public void everyCategoryHasArtworkAndSoDoesNoCategoryAtAll() {
        for (AttractionCategory category : AttractionCategory.values()) {
            assertNotEquals("no artwork for " + category, 0, AttractionArtwork.artworkFor(category));
        }
        assertNotEquals(0, AttractionArtwork.artworkFor(null));
    }

    @Test
    public void aPlaceWithNoBundledPhotographStillGetsItsKindsArtwork() {
        for (AttractionCategory category : AttractionCategory.values()) {
            int row = AttractionArtwork.drawableFor("osm:node/1", category);
            int hero = AttractionArtwork.heroDrawableFor("osm:node/1", category);
            assertNotEquals(0, row);
            assertNotEquals(0, hero);
            assertNotEquals("a fetched place must not borrow a seed's photograph",
                    AttractionArtwork.drawableFor("old-arts", category), row);
        }
    }

    @Test
    public void theKindsAreToldApartRatherThanSharingOneDrawing() {
        Set<Integer> drawables = new HashSet<>();
        for (AttractionCategory category : AttractionCategory.values()) {
            drawables.add(AttractionArtwork.artworkFor(category));
        }
        // Nine categories, eight drawings: the two landmark kinds share the building.
        assertTrue("expected several distinct drawings, got " + drawables.size(),
                drawables.size() >= 6);
    }

    @Test
    public void aSeedPlaceKeepsItsOwnPhotograph() {
        int seed = AttractionArtwork.drawableFor("system-garden", AttractionCategory.PARK);
        assertNotEquals(AttractionArtwork.artworkFor(AttractionCategory.PARK), seed);
    }
}
