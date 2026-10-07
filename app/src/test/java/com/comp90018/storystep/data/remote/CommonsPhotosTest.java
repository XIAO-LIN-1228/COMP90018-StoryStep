package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

public class CommonsPhotosTest {

    private static CommonsGeosearchDto parse(String json) {
        return new Gson().fromJson(json, CommonsGeosearchDto.class);
    }

    private static String page(String id, String title, String mime, int w, int h) {
        return "\"" + id + "\":{\"title\":\"" + title + "\",\"imageinfo\":[{\"thumburl\":\"https://x/"
                + id + ".jpg\",\"url\":\"https://x/full" + id + "\",\"width\":" + w + ",\"height\":"
                + h + ",\"mime\":\"" + mime + "\"}]}";
    }

    @Test
    public void theLargestJpegWinsAndMapsPlansAndSmallFilesAreSkipped() {
        CommonsGeosearchDto dto = parse("{\"query\":{\"pages\":{"
                + page("1", "File:Old Arts.jpg", "image/jpeg", 3000, 2000) + ","
                + page("2", "File:Old Arts huge.jpg", "image/jpeg", 6000, 4000) + ","
                + page("3", "File:Campus map.jpg", "image/jpeg", 8000, 8000) + ","
                + page("4", "File:Plan of the building.jpg", "image/jpeg", 8000, 8000) + ","
                + page("5", "File:Logo.png", "image/png", 8000, 8000) + ","
                + page("6", "File:Tiny.jpg", "image/jpeg", 640, 480) + "}}}");
        assertEquals("https://x/2.jpg", CommonsPhotos.pick(dto));
    }

    @Test
    public void aPortraitFrameLosesToALandscapeOfHalfThePixels() {
        CommonsGeosearchDto dto = parse("{\"query\":{\"pages\":{"
                + page("p", "File:Tower.jpg", "image/jpeg", 2000, 4000) + ","
                + page("l", "File:Tower wide.jpg", "image/jpeg", 3000, 1600) + "}}}");
        assertEquals("https://x/l.jpg", CommonsPhotos.pick(dto));
    }

    @Test
    public void nothingUsableIsNull() {
        assertNull(CommonsPhotos.pick(null));
        assertNull(CommonsPhotos.pick(parse("{\"batchcomplete\":\"\"}")));
        assertNull(CommonsPhotos.pick(parse("{\"query\":{\"pages\":{"
                + page("1", "File:Small.jpg", "image/jpeg", 800, 600) + "}}}")));
    }

    @Test
    public void coordinatesAreSixDecimalsPipeSeparated() {
        assertEquals("-37.798100|144.959100", CommonsPhotos.coordinate(-37.7981, 144.9591));
    }

    @Test
    public void theAreaPassGivesEachPlaceTheBestPhotographWithinReach() {
        CommonsGeosearchDto dto = parse("{\"query\":{\"pages\":{"
                + "\"a\":{\"title\":\"File:IMG 0001.jpg\",\"coordinates\":[{\"lat\":-37.7981,\"lon\":144.9591}],"
                + "\"imageinfo\":[{\"width\":1200,\"height\":800,\"mime\":\"image/jpeg\"}]},"
                + "\"b\":{\"title\":\"File:IMG 0002.jpg\",\"coordinates\":[{\"lat\":-37.7982,\"lon\":144.9592}],"
                + "\"imageinfo\":[{\"width\":4000,\"height\":3000,\"mime\":\"image/jpeg\"}]},"
                + "\"c\":{\"title\":\"File:IMG 0003.jpg\",\"coordinates\":[{\"lat\":-37.81,\"lon\":144.99}],"
                + "\"imageinfo\":[{\"width\":4000,\"height\":3000,\"mime\":\"image/jpeg\"}]}}}}");
        java.util.List<com.comp90018.storystep.domain.model.GeoPoint> places = java.util.Arrays.asList(
                new com.comp90018.storystep.domain.model.GeoPoint(-37.7981, 144.9591),
                null,
                new com.comp90018.storystep.domain.model.GeoPoint(-37.75, 144.90));
        java.util.Map<Integer, String> chosen = CommonsPhotos.assign(dto, places, 80);
        assertEquals(1, chosen.size());
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/IMG_0002.jpg?width=1280",
                chosen.get(0));
    }

    @Test
    public void aFileBeyondTwentyFiveMetresNeedsAWordOfThePlacesName() {
        CommonsGeosearchDto dto = parse("{\"query\":{\"pages\":{"
                + "\"a\":{\"title\":\"File:Medical building east wing.jpg\",\"coordinates\":[{\"lat\":-37.7985,\"lon\":144.9591}],"
                + "\"imageinfo\":[{\"width\":6000,\"height\":4000,\"mime\":\"image/jpeg\"}]},"
                + "\"b\":{\"title\":\"File:University Cenotaph Parkville.jpg\",\"coordinates\":[{\"lat\":-37.7986,\"lon\":144.9592}],"
                + "\"imageinfo\":[{\"width\":1200,\"height\":900,\"mime\":\"image/jpeg\"}]}}}}");
        // The place is ~45 m from both files: the named one wins despite being smaller.
        java.util.List<com.comp90018.storystep.domain.model.GeoPoint> places = java.util.Collections.singletonList(
                new com.comp90018.storystep.domain.model.GeoPoint(-37.7981, 144.9591));
        java.util.Map<Integer, String> chosen = CommonsPhotos.assign(dto, places,
                java.util.Collections.singletonList("University Cenotaph"), 80);
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/University_Cenotaph_Parkville.jpg?width=1280",
                chosen.get(0));
        // With a name that matches nothing, neither file is close enough.
        assertTrue(CommonsPhotos.assign(dto, places,
                java.util.Collections.singletonList("Something Else"), 80).isEmpty());
    }

    @Test
    public void genericWordsDoNotTieAFileToAPlace() {
        assertTrue(CommonsPhotos.sharesAWord("University Cenotaph", "File:Cenotaph 2019.jpg"));
        assertTrue(!CommonsPhotos.sharesAWord("Melbourne Museum", "File:Melbourne skyline.jpg"));
        assertTrue(!CommonsPhotos.sharesAWord("Big Clock", "File:Tower at dusk.jpg"));
        // One shared word is not enough for a name that has several.
        assertTrue(!CommonsPhotos.sharesAWord("John F. Kennedy Memorial", "File:Sir William John Clarke.jpg"));
        assertTrue(!CommonsPhotos.sharesAWord("Major General Charles George Gordon", "File:Adam Lindsay Gordon Statue.jpg"));
        assertTrue(CommonsPhotos.sharesAWord("John F. Kennedy Memorial", "File:Kennedy memorial John.jpg"));
        assertTrue(CommonsPhotos.sharesAWord("Melbourne Skydeck", "Eureka Skydeck"));
    }

    @Test
    public void aFileNamedForSomethingElseIsRejectedHoweverClose() {
        CommonsGeosearchDto dto = parse("{\"query\":{\"pages\":{"
                + "\"a\":{\"title\":\"File:Wilson Hall 2.jpg\",\"coordinates\":[{\"lat\":-37.7981,\"lon\":144.9591}],"
                + "\"imageinfo\":[{\"width\":4000,\"height\":3000,\"mime\":\"image/jpeg\"}]}}}}");
        java.util.List<com.comp90018.storystep.domain.model.GeoPoint> places = java.util.Collections.singletonList(
                new com.comp90018.storystep.domain.model.GeoPoint(-37.7981, 144.9591));
        assertTrue(CommonsPhotos.assign(dto, places,
                java.util.Collections.singletonList("University Cenotaph"), 80).isEmpty());
        assertTrue(CommonsPhotos.namesSomethingElse("University Cenotaph", "File:Wilson Hall 2.jpg"));
        assertTrue(!CommonsPhotos.namesSomethingElse("University Cenotaph", "File:IMG 4021.jpg"));
        assertTrue(!CommonsPhotos.namesSomethingElse("University Cenotaph", "File:Melbourne 2019.jpg"));
        assertTrue(CommonsPhotos.namesSomethingElse("University Cenotaph", "File:The Old Arts Building.jpg"));
    }

    @Test
    public void aSearchResultCountsOnlyWhenItsTitleIsReallyThePlacesName() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        CommonsSearchDto found = gson.fromJson("{\"query\":{\"search\":["
                + "{\"title\":\"File:Prince Alfred Hotel, Carlton.jpg\"}]}}", CommonsSearchDto.class);
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/"
                        + "Prince_Alfred_Hotel%2C_Carlton.jpg?width=1280",
                CommonsPhotos.pickNamed(found, "Prince Alfred Hotel"));

        // A text search answers with something for almost any query; a library that is not this
        // library must not be shown as it.
        CommonsSearchDto wrong = gson.fromJson("{\"query\":{\"search\":["
                + "{\"title\":\"File:State Library Victoria reading room.jpg\"}]}}",
                CommonsSearchDto.class);
        assertNull(CommonsPhotos.pickNamed(wrong, "Southbank Library"));

        // Maps and plans are no more wanted here than in the geosearch results.
        CommonsSearchDto plan = gson.fromJson("{\"query\":{\"search\":["
                + "{\"title\":\"File:Prince Alfred Hotel floor plan.svg\"}]}}", CommonsSearchDto.class);
        assertNull(CommonsPhotos.pickNamed(plan, "Prince Alfred Hotel"));

        assertNull(CommonsPhotos.pickNamed(null, "Anything"));
        assertNull(CommonsPhotos.pickNamed(found, null));
    }
}
