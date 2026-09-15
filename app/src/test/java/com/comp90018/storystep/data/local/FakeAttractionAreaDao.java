package com.comp90018.storystep.data.local;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link AttractionAreaDao}.
 *
 * <p>Keeps the whole row rather than just the timestamp, so a test can assert that an area was
 * recorded as fetched with nothing in it — the case that separates a quiet neighbourhood from a
 * provider failure, and the one the real table exists to make representable.
 */
public final class FakeAttractionAreaDao implements AttractionAreaDao {

    private final Map<String, AttractionAreaEntity> rows = new LinkedHashMap<>();

    /** Set to make every read throw, so the repository's unreadable-cache path is reachable. */
    public boolean failReads;

    @Override
    public AttractionAreaEntity findByKey(String areaKey) {
        if (failReads) {
            throw new IllegalStateException("area table unreadable");
        }
        return rows.get(areaKey);
    }

    @Override
    public void upsert(AttractionAreaEntity area) {
        rows.put(area.areaKey, area);
    }

    @Override
    public int deleteAll() {
        int removed = rows.size();
        rows.clear();
        return removed;
    }

    @Override
    public int count() {
        return rows.size();
    }
}
