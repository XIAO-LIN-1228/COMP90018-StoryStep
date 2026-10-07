package com.comp90018.storystep.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.AttractionCategory;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class AttractionArtworkTest {

    @Test
    public void everyCategoryHasItsOwnIllustration() {
        Set<Integer> seen = new HashSet<>();
        for (AttractionCategory category : AttractionCategory.values()) {
            int drawable = AttractionArtwork.categoryDrawable(category);
            assertNotEquals(category.name(), R.drawable.bg_photo_placeholder, drawable);
            assertTrue(category.name() + " shares an illustration", seen.add(drawable));
        }
    }

    @Test
    public void anUnknownCategoryKeepsTheNeutralPlaceholder() {
        assertEquals(R.drawable.bg_photo_placeholder, AttractionArtwork.categoryDrawable(null));
        assertEquals(R.drawable.bg_photo_placeholder,
                AttractionArtwork.drawableFor("node/1", null));
    }

    @Test
    public void bundledArtworkStillWinsOverTheCategory() {
        assertEquals(R.drawable.photo_system_garden,
                AttractionArtwork.drawableFor("system-garden", AttractionCategory.PARK));
    }

    @Test
    public void aFetchedPlaceFallsBackToItsCategory() {
        assertEquals(R.drawable.art_category_public_art,
                AttractionArtwork.drawableFor("node/1", AttractionCategory.PUBLIC_ART));
    }
}
