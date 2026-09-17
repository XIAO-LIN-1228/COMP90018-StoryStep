package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.Map;

public class WikidataImagesTest {

    private static final String SAMPLE = "{\"entities\":{"
            + "\"Q1747074\":{\"claims\":{"
            + "\"P31\":[{\"mainsnak\":{\"datavalue\":{\"value\":{\"entity-type\":\"item\",\"id\":\"Q41176\"}}}}],"
            + "\"P18\":[{\"mainsnak\":{\"datavalue\":"
            + "{\"value\":\"Old Arts Building, Melbourne.jpg\"}}}]}},"
            + "\"Q2\":{\"claims\":{\"P31\":[{\"mainsnak\":{\"datavalue\":{\"value\":\"x\"}}}]}},"
            + "\"Q3\":{\"claims\":{\"P18\":[{\"mainsnak\":{}}]}},"
            + "\"Q4\":{}"
            + "}}";

    @Test
    public void readsTheFirstImageOfEveryEntityThatHasOne() {
        WikidataEntitiesDto dto = new Gson().fromJson(SAMPLE, WikidataEntitiesDto.class);
        Map<String, String> urls = WikidataImages.imageUrls(dto);
        assertEquals(1, urls.size());
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/"
                + "Old_Arts_Building%2C_Melbourne.jpg?width=1280", urls.get("Q1747074"));
    }

    @Test
    public void nothingUsableYieldsNothing() {
        assertTrue(WikidataImages.imageUrls(null).isEmpty());
        assertTrue(WikidataImages.imageUrls(new WikidataEntitiesDto()).isEmpty());
    }

    @Test
    public void onlyAWellFormedWikidataReferenceYieldsAnId() {
        assertEquals("Q1747074", WikidataImages.wikidataId("wikidata:Q1747074"));
        assertNull(WikidataImages.wikidataId("wikipedia:en:Old Arts Building"));
        assertNull(WikidataImages.wikidataId("wikidata:not-an-id"));
        assertNull(WikidataImages.wikidataId(null));
    }
}
