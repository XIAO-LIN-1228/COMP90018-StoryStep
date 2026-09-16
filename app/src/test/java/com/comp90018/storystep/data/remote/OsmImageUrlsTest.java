package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OsmImageUrlsTest {

    private static Map<String, String> tags(String key, String value) {
        Map<String, String> tags = new HashMap<>();
        tags.put(key, value);
        return tags;
    }

    @Test
    public void nothingYieldsNothing() {
        assertNull(OsmImageUrls.fromTags(null));
        assertNull(OsmImageUrls.fromTags(Collections.<String, String>emptyMap()));
        assertNull(OsmImageUrls.fromTags(tags("image", "   ")));
    }

    @Test
    public void httpsImageUrlIsUsedAsIs() {
        assertEquals("https://example.org/a.jpg",
                OsmImageUrls.fromTags(tags("image", "https://example.org/a.jpg")));
    }

    @Test
    public void plainHttpIsUpgradedNotDropped() {
        assertEquals("https://example.org/a.jpg",
                OsmImageUrls.fromTags(tags("image", "http://example.org/a.jpg")));
    }

    @Test
    public void firstOfSeveralImagesWins() {
        assertEquals("https://a.org/1.jpg",
                OsmImageUrls.fromTags(tags("image", "https://a.org/1.jpg; https://a.org/2.jpg")));
    }

    @Test
    public void commonsFileResolvesThroughFilePathWithAWidth() {
        assertEquals(
                "https://commons.wikimedia.org/wiki/Special:FilePath/Old_Arts_Building.jpg?width=800",
                OsmImageUrls.fromTags(tags("wikimedia_commons", "File:Old Arts Building.jpg")));
    }

    @Test
    public void commonsCategoryIsAFolderNotAPicture() {
        assertNull(OsmImageUrls.fromTags(tags("wikimedia_commons", "Category:Old Arts Building")));
    }

    @Test
    public void imageTagMayItselfBeACommonsFile() {
        assertEquals(
                "https://commons.wikimedia.org/wiki/Special:FilePath/Garden.jpg?width=800",
                OsmImageUrls.fromTags(tags("image", "File:Garden.jpg")));
    }

    @Test
    public void imageTagWinsOverCommonsTag() {
        Map<String, String> tags = new HashMap<>();
        tags.put("image", "https://example.org/a.jpg");
        tags.put("wikimedia_commons", "File:Other.jpg");
        assertEquals("https://example.org/a.jpg", OsmImageUrls.fromTags(tags));
    }

    @Test
    public void unusableImageTagFallsBackToCommons() {
        Map<String, String> tags = new HashMap<>();
        tags.put("image", "ftp://old.example.org/a.jpg");
        tags.put("wikimedia_commons", "File:Other.jpg");
        assertEquals(
                "https://commons.wikimedia.org/wiki/Special:FilePath/Other.jpg?width=800",
                OsmImageUrls.fromTags(tags));
    }

    @Test
    public void specialCharactersInFileNamesAreEncoded() {
        assertEquals(
                "https://commons.wikimedia.org/wiki/Special:FilePath/Caf%C3%A9_%26_Bar.jpg?width=800",
                OsmImageUrls.fromTags(tags("wikimedia_commons", "File:Café & Bar.jpg")));
    }
}
