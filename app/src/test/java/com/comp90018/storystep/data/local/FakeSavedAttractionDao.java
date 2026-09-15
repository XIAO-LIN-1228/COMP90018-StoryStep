package com.comp90018.storystep.data.local;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * In-memory {@link SavedAttractionDao} that honours the real table's primary key on
 * {@code attraction_id}: a second insert for the same place is ignored rather than stored.
 *
 * <p>That ignoring is the behaviour the repository's idempotence rests on, so a fake that appended
 * a second row would let a duplicate-save bug pass the JVM tests and only appear on a device.
 */
public final class FakeSavedAttractionDao implements SavedAttractionDao {

    private final List<SavedAttractionEntity> rows = new ArrayList<>();

    /** Set to make the next call throw, standing in for a storage failure. */
    public RuntimeException failWrites;

    @Override
    public List<SavedAttractionEntity> findAllByRecency() {
        failIfRequested();
        List<SavedAttractionEntity> found = new ArrayList<>();
        for (SavedAttractionEntity row : rows) {
            found.add(copy(row));
        }
        found.sort(Comparator
                .comparingLong((SavedAttractionEntity row) -> row.savedAtMillis).reversed()
                .thenComparing(row -> row.attractionId));
        return found;
    }

    @Override
    public List<String> findAllIds() {
        failIfRequested();
        List<String> ids = new ArrayList<>();
        for (SavedAttractionEntity row : findAllByRecency()) {
            ids.add(row.attractionId);
        }
        return ids;
    }

    @Override
    public SavedAttractionEntity findById(String attractionId) {
        for (SavedAttractionEntity row : rows) {
            if (row.attractionId.equals(attractionId)) {
                return copy(row);
            }
        }
        return null;
    }

    @Override
    public long insertIgnoring(SavedAttractionEntity saved) {
        failIfRequested();
        if (findById(saved.attractionId) != null) {
            return -1L;
        }
        rows.add(copy(saved));
        return rows.size();
    }

    @Override
    public int deleteById(String attractionId) {
        failIfRequested();
        for (Iterator<SavedAttractionEntity> iterator = rows.iterator(); iterator.hasNext(); ) {
            if (iterator.next().attractionId.equals(attractionId)) {
                iterator.remove();
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteAll() {
        failIfRequested();
        int removed = rows.size();
        rows.clear();
        return removed;
    }

    @Override
    public int count() {
        return rows.size();
    }

    private void failIfRequested() {
        if (failWrites != null) {
            throw failWrites;
        }
    }

    private static SavedAttractionEntity copy(SavedAttractionEntity source) {
        SavedAttractionEntity copy = new SavedAttractionEntity();
        copy.attractionId = source.attractionId;
        copy.name = source.name;
        copy.category = source.category;
        copy.description = source.description;
        copy.indoor = source.indoor;
        copy.latitude = source.latitude;
        copy.longitude = source.longitude;
        copy.savedAtMillis = source.savedAtMillis;
        copy.provenance = source.provenance;
        copy.sourceRef = source.sourceRef;
        return copy;
    }
}
