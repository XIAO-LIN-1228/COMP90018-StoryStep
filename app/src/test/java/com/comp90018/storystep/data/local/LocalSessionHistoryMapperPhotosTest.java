package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class LocalSessionHistoryMapperPhotosTest {

    @Test
    public void aNullColumnReadsAsNoPhotographs() {
        assertTrue(LocalSessionHistoryMapper.splitPhotoFiles(null).isEmpty());
    }

    @Test
    public void namesAreOnePerLineAndBlankLinesAreIgnored() {
        assertEquals(Arrays.asList("a.jpg", "b.jpg"),
                LocalSessionHistoryMapper.splitPhotoFiles("a.jpg\n\n b.jpg \n"));
    }

    @Test
    public void joiningRoundTripsAndNoneIsNull() {
        assertNull(LocalSessionHistoryMapper.joinPhotoFiles(Collections.<String>emptyList()));
        assertNull(LocalSessionHistoryMapper.joinPhotoFiles(null));
        String column = LocalSessionHistoryMapper.joinPhotoFiles(Arrays.asList("a.jpg", "b.jpg"));
        assertEquals("a.jpg\nb.jpg", column);
        assertEquals(Arrays.asList("a.jpg", "b.jpg"),
                LocalSessionHistoryMapper.splitPhotoFiles(column));
    }
}
