package com.comp90018.storystep.data.local;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link AttractionDao}.
 *
 * <p>{@code upsertAll} replaces and {@code insertMissing} does not, mirroring the two conflict
 * strategies the real DAO declares — the difference between them is exactly what keeps a seeded
 * copy from overwriting a row fetched from a real service.
 */
public final class FakeAttractionDao implements AttractionDao {

    private final Map<String, AttractionEntity> rows = new LinkedHashMap<>();

    /** How many times a seeding insert was attempted, whether or not it wrote anything. */
    public int insertMissingCalls;

    @Override
    public List<AttractionEntity> findAll() {
        List<AttractionEntity> all = new ArrayList<>(rows.values());
        all.sort(Comparator.comparing(entity -> entity.name == null ? "" : entity.name));
        return all;
    }

    @Override
    public AttractionEntity findById(String attractionId) {
        return rows.get(attractionId);
    }

    @Override
    public void upsertAll(List<AttractionEntity> attractions) {
        for (AttractionEntity entity : attractions) {
            rows.put(entity.id, entity);
        }
    }

    @Override
    public void insertMissing(List<AttractionEntity> attractions) {
        insertMissingCalls++;
        for (AttractionEntity entity : attractions) {
            if (!rows.containsKey(entity.id)) {
                rows.put(entity.id, entity);
            }
        }
    }

    @Override
    public int deleteProviderRowsWithin(String seedProvenance,
                                        double minLatitude,
                                        double maxLatitude,
                                        double minLongitude,
                                        double maxLongitude) {
        int removed = 0;
        Iterator<AttractionEntity> iterator = rows.values().iterator();
        while (iterator.hasNext()) {
            AttractionEntity entity = iterator.next();
            boolean fromProvider = !seedProvenance.equals(entity.provenance);
            boolean inBox = entity.latitude >= minLatitude && entity.latitude <= maxLatitude
                    && entity.longitude >= minLongitude && entity.longitude <= maxLongitude;
            if (fromProvider && inBox) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public int count() {
        return rows.size();
    }

    @Override
    public void deleteAll() {
        rows.clear();
    }
}
