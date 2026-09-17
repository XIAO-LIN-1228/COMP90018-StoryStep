package com.comp90018.storystep.data.local;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns stored session rows into the history model.
 *
 * <p>Defensive on the way out, in the same way {@code LocalWeatherMapper} is. These rows can
 * predate version 5, in which case their context columns are the defaults the migration wrote
 * rather than anything a session recorded, and a row can in principle be malformed. Every such case
 * reads back as "not recorded" — history showing nothing is honest, history showing 0 °C on a walk
 * that never recorded weather is not.
 */
public final class LocalSessionHistoryMapper {

    private LocalSessionHistoryMapper() {
        // Utility class.
    }

    public static SessionHistoryEntry toEntry(SessionHistoryRow row) {
        return new SessionHistoryEntry(
                row.id,
                row.startWallClockMillis,
                row.endWallClockMillis,
                row.activeDurationMillis,
                row.distanceMeters,
                row.visitCount,
                row.segmentCount,
                row.interrupted,
                row.destinationName,
                row.title,
                row.placeName,
                splitPhotoFiles(row.photoFiles));
    }

    /** One file name per line; blank lines and a null column both read as no photographs. */
    public static List<String> splitPhotoFiles(String column) {
        List<String> files = new ArrayList<>();
        if (column == null) {
            return files;
        }
        for (String line : column.split("\n")) {
            String name = line.trim();
            if (!name.isEmpty()) {
                files.add(name);
            }
        }
        return files;
    }

    /** The inverse of {@link #splitPhotoFiles}: null for none, so the column stays honest. */
    public static String joinPhotoFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (String file : files) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(file);
        }
        return joined.toString();
    }

    public static List<SessionHistoryEntry> toEntries(List<SessionHistoryRow> rows) {
        List<SessionHistoryEntry> entries = new ArrayList<>(rows.size());
        for (SessionHistoryRow row : rows) {
            entries.add(toEntry(row));
        }
        return entries;
    }

    /** The stored session as a history entry, with the visit count supplied by the caller. */
    public static SessionHistoryEntry toEntry(SessionEntity session, int visitCount) {
        return new SessionHistoryEntry(
                session.id,
                session.startWallClockMillis,
                session.endWallClockMillis,
                session.activeDurationMillis,
                session.distanceMeters,
                visitCount,
                session.segmentCount,
                session.interrupted,
                session.destinationName,
                session.title,
                session.placeName,
                splitPhotoFiles(session.photoFiles));
    }

    /**
     * The movement recorded for the session.
     *
     * <p>A null state means the row predates version 5, which is reported as
     * {@code UNAVAILABLE} — the same answer a device with no motion sensor gives, and the only
     * honest one for a session whose movement was never recorded.
     */
    public static MovementSnapshot toMovement(SessionEntity session) {
        MovementState state = parseState(session.movementState);
        if (state == null || state == MovementState.UNAVAILABLE) {
            return MovementSnapshot.unavailable();
        }
        return new MovementSnapshot(state,
                Math.max(0L, session.movementStationaryMillis),
                Math.max(0L, session.movementMovingMillis));
    }

    /**
     * The weather recorded for the session, or null when none was.
     *
     * <p>The area key is the discriminator for the whole group: without it there is nothing to say
     * where the observation applied, so there is no observation. The instants are checked too
     * because {@code WeatherSnapshot} rejects non-positive ones, and a version 4 row migrated
     * forward holds zeros.
     */
    public static WeatherSnapshot toWeather(SessionEntity session) {
        if (session.weatherAreaKey == null || session.weatherAreaKey.isEmpty()) {
            return null;
        }
        if (session.weatherObservedAtMillis <= 0L || session.weatherFetchedAtMillis <= 0L) {
            return null;
        }
        WeatherCondition condition = parseCondition(session.weatherCondition);
        try {
            return new WeatherSnapshot(
                    session.weatherAreaKey,
                    condition,
                    session.weatherTemperatureCelsius,
                    session.weatherObservedAtMillis,
                    session.weatherFetchedAtMillis,
                    WeatherSnapshot.Provenance.CACHE);
        } catch (IllegalArgumentException malformed) {
            // A stored row the model refuses is a row that cannot be presented honestly. Reporting
            // no weather is better than letting a broken value reach a screen.
            return null;
        }
    }

    public static RoutePoint toRoutePoint(RoutePointEntity entity) {
        return new RoutePoint(
                new GeoPoint(entity.latitude, entity.longitude),
                entity.accuracyMeters,
                entity.wallClockMillis,
                Math.max(1, entity.segment));
    }

    public static List<RoutePoint> toRoutePoints(List<RoutePointEntity> entities) {
        List<RoutePoint> points = new ArrayList<>(entities.size());
        for (RoutePointEntity entity : entities) {
            points.add(toRoutePoint(entity));
        }
        return points;
    }

    private static MovementState parseState(String name) {
        if (name == null) {
            return null;
        }
        try {
            return MovementState.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static WeatherCondition parseCondition(String name) {
        if (name == null) {
            return WeatherCondition.UNKNOWN;
        }
        try {
            return WeatherCondition.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return WeatherCondition.UNKNOWN;
        }
    }
}
