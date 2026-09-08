package com.comp90018.storystep.data.repository;

import androidx.annotation.Nullable;

import com.comp90018.storystep.data.AttractionProvenanceCodec;
import com.comp90018.storystep.data.local.AttractionAreaDao;
import com.comp90018.storystep.data.local.AttractionAreaEntity;
import com.comp90018.storystep.data.local.AttractionDao;
import com.comp90018.storystep.data.local.AttractionEntity;
import com.comp90018.storystep.data.local.LocalAttractionMapper;
import com.comp90018.storystep.data.local.Transactions;
import com.comp90018.storystep.domain.geo.BoundingBox;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.time.Clock;

import java.util.List;
import java.util.Objects;

/**
 * The stored attraction content and the bookkeeping of which areas have been fetched.
 *
 * <p>Synchronous and blocking: every method here must be called on an I/O thread, and scheduling
 * belongs to the repositories above. That is what lets both {@link RoomAttractionRepository} and
 * {@link CachedAttractionRepository} share one copy of the seeding rule and one copy of the write
 * policy, instead of each keeping its own and drifting apart.
 */
public final class AttractionCache {

    private final AttractionDao attractionDao;
    private final AttractionAreaDao areaDao;
    private final Transactions transactions;
    private final Clock clock;
    private final List<Attraction> seed;

    /** Set once the seed has been confirmed present, so later queries skip the check. */
    private volatile boolean seeded;

    public AttractionCache(AttractionDao attractionDao,
                           AttractionAreaDao areaDao,
                           Transactions transactions,
                           Clock clock,
                           List<Attraction> seed) {
        this.attractionDao = Objects.requireNonNull(attractionDao, "attractionDao");
        this.areaDao = Objects.requireNonNull(areaDao, "areaDao");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.seed = Objects.requireNonNull(seed, "seed");
    }

    /**
     * Writes the bundled demonstration set once.
     *
     * <p>Idempotent twice over: the {@code seeded} flag skips the work within a process, and the
     * insert ignores ids that already exist, so a relaunch — or two queries racing on the two I/O
     * threads — cannot produce duplicate rows. Existing rows are never overwritten, because a row
     * fetched from a real service must not be replaced by the bundled copy of the same place.
     *
     * <p>Note what this deliberately does <i>not</i> check. An earlier version skipped the insert
     * whenever the table held any row at all, which was safe while seeding was the only thing that
     * ever wrote to it. Once a provider could write first, a cold start that fetched before it read
     * left the table non-empty and the bundled set never arrived — permanently, on that install.
     * The primary-key conflict rule already makes the insert harmless, so the row count is not
     * consulted.
     */
    public void ensureSeeded() {
        if (seeded) {
            return;
        }
        synchronized (this) {
            if (seeded) {
                return;
            }
            List<AttractionEntity> rows =
                    LocalAttractionMapper.toEntities(seed, clock.wallClockMillis());
            attractionDao.insertMissing(rows);
            seeded = true;
        }
    }

    /** Everything stored, seed and fetched alike. Seeds first if it has not happened yet. */
    public List<Attraction> readAll() {
        ensureSeeded();
        return LocalAttractionMapper.toDomainList(attractionDao.findAll());
    }

    /** One stored record, or {@code null} when nothing is stored under that id. */
    @Nullable
    public Attraction findById(String attractionId) {
        ensureSeeded();
        AttractionEntity entity = attractionDao.findById(attractionId);
        return entity == null ? null : LocalAttractionMapper.toDomain(entity);
    }

    /**
     * Records a successful fetch: replaces the provider content and marks the area as fetched.
     *
     * <p>Atomic because a half-written refresh would leave the screen showing a set that never
     * existed, and because the area row must not claim a fetch that the content write did not
     * complete.
     *
     * <p>The stale rows the fetch supersedes are cleared first, but only the provider rows inside
     * the box {@link BoundingBox#inscribedIn} fits within the requested circle. That bound is what
     * keeps a refresh of one area from emptying the areas around it — see
     * {@code AttractionDao.deleteProviderRowsWithin}. Where no honest box can be computed the
     * delete is skipped and the write degrades to an upsert: superseded rows linger, which is a
     * far smaller problem than removing content nothing is going to put back.
     *
     * <p>An empty {@code attractions} list is a legitimate outcome and is recorded as one. The
     * caller is responsible for never reaching here with a payload it could not trust — an area
     * marked fetched is not re-requested for a day.
     *
     * @param areaKey         the grid cell that was fetched
     * @param center          the coordinate the request was centred on
     * @param radiusMeters    the radius that was requested
     * @param attractions     the validated records, possibly empty
     * @param fetchedAtMillis the wall clock to stamp both the rows and the area with
     */
    public void storeFetched(String areaKey,
                             GeoPoint center,
                             double radiusMeters,
                             List<Attraction> attractions,
                             long fetchedAtMillis) {
        Objects.requireNonNull(areaKey, "areaKey");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(attractions, "attractions");
        BoundingBox replaced = BoundingBox.inscribedIn(center, radiusMeters);
        transactions.runInTransaction(() -> {
            if (replaced != null) {
                attractionDao.deleteProviderRowsWithin(
                        AttractionProvenanceCodec.CODE_SEED,
                        replaced.getMinLatitude(), replaced.getMaxLatitude(),
                        replaced.getMinLongitude(), replaced.getMaxLongitude());
            }
            if (!attractions.isEmpty()) {
                attractionDao.upsertAll(
                        LocalAttractionMapper.toEntities(attractions, fetchedAtMillis));
            }
            AttractionAreaEntity area = new AttractionAreaEntity();
            area.areaKey = areaKey;
            area.fetchedAtMillis = fetchedAtMillis;
            area.recordCount = attractions.size();
            areaDao.upsert(area);
        });
    }

    /**
     * When the given area was last fetched.
     *
     * @return the record, or {@code null} when this area has never been fetched — which is not the
     *         same as an area that was fetched and held nothing
     */
    @Nullable
    public AttractionAreaEntity findArea(String areaKey) {
        return areaDao.findByKey(areaKey);
    }

    /** Forgets every fetch, so the next query goes to the network. Stored content is untouched. */
    public int clearAreas() {
        return areaDao.deleteAll();
    }
}
