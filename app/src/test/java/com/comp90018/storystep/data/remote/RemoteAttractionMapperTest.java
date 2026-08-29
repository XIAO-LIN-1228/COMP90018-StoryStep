package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;

import org.junit.Test;

import java.util.Arrays;

/** The remote boundary must drop unusable records instead of propagating them. */
public class RemoteAttractionMapperTest {

    private static AttractionDto validDto() {
        AttractionDto dto = new AttractionDto();
        dto.id = "potter-museum";
        dto.name = "Ian Potter Museum of Art";
        dto.category = "museum";
        dto.description = "University art museum.";
        dto.indoor = Boolean.TRUE;
        dto.latitude = -37.79750;
        dto.longitude = 144.96400;
        return dto;
    }

    @Test
    public void mapsACompleteRecord() {
        Attraction attraction = RemoteAttractionMapper.toDomain(validDto());

        assertNotNull(attraction);
        assertEquals("potter-museum", attraction.getId());
        assertEquals(AttractionCategory.MUSEUM, attraction.getCategory());
        assertEquals(true, attraction.isIndoor());
        assertEquals(-37.79750, attraction.getLocation().getLatitude(), 1e-9);
    }

    @Test
    public void unknownCategoryFallsBackInsteadOfFailing() {
        AttractionDto dto = validDto();
        dto.category = "space-elevator";

        Attraction attraction = RemoteAttractionMapper.toDomain(dto);

        assertNotNull(attraction);
        assertEquals(AttractionCategory.CULTURAL_SITE, attraction.getCategory());
    }

    @Test
    public void missingOptionalFieldsGetSafeDefaults() {
        AttractionDto dto = validDto();
        dto.description = null;
        dto.indoor = null;
        dto.category = null;

        Attraction attraction = RemoteAttractionMapper.toDomain(dto);

        assertNotNull(attraction);
        assertEquals("", attraction.getDescription());
        assertFalse(attraction.isIndoor());
    }

    @Test
    public void recordsMissingRequiredFieldsAreRejected() {
        AttractionDto noId = validDto();
        noId.id = "  ";
        assertNull(RemoteAttractionMapper.toDomain(noId));

        AttractionDto noName = validDto();
        noName.name = null;
        assertNull(RemoteAttractionMapper.toDomain(noName));

        AttractionDto noCoordinate = validDto();
        noCoordinate.latitude = null;
        assertNull(RemoteAttractionMapper.toDomain(noCoordinate));

        assertNull(RemoteAttractionMapper.toDomain(null));
    }

    @Test
    public void outOfRangeCoordinatesAreRejected() {
        AttractionDto dto = validDto();
        dto.latitude = 120.0;

        assertNull(RemoteAttractionMapper.toDomain(dto));
    }

    @Test
    public void listMappingSkipsUnusableEntries() {
        AttractionDto broken = validDto();
        broken.id = null;

        assertEquals(1, RemoteAttractionMapper.toDomainList(
                Arrays.asList(validDto(), broken, null)).size());
        assertEquals(0, RemoteAttractionMapper.toDomainList(null).size());
    }
}
