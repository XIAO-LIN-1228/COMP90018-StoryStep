package com.comp90018.storystep.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.comp90018.storystep.data.local.RoutePointEntity;
import com.comp90018.storystep.data.local.SessionEntity;
import com.comp90018.storystep.data.local.StoryStepDatabase;
import com.comp90018.storystep.data.local.VisitEntity;
import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Three published walks a fresh install starts with, so the journeys page is a feed and not
 * an empty state until the first real walk is finished.
 *
 * <p>Seeded once, on the first launch that finds no finished walk, and marked as done so a
 * user who deletes them is not given them back. Each is a real route through Melbourne with
 * its own photographs from Wikimedia Commons - the same source the places use - stored as
 * URLs where a walk the user publishes stores file names. They are ordinary finished sessions
 * otherwise: they open, they count, and they can be deleted.
 */
public final class SampleJourneys {

    private static final String PREFERENCES = "sample_journeys";
    private static final String KEY_SEEDED = "seeded_v1";

    private static final String COMMONS = "https://commons.wikimedia.org/wiki/Special:FilePath/";
    private static final String SCALE = "?width=1280";

    private SampleJourneys() {
    }

    /** Blocking; run on the I/O executor before the journeys page can be reached. */
    public static void seedIfNeeded(Context context, StoryStepDatabase database, long nowMillis) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        if (preferences.getBoolean(KEY_SEEDED, false)) {
            return;
        }
        if (database.sessionDao().countFinished() > 0) {
            preferences.edit().putBoolean(KEY_SEEDED, true).apply();
            return;
        }
        database.runInTransaction(() -> {
            for (Walk walk : walks(nowMillis)) {
                insert(database, walk);
            }
        });
        preferences.edit().putBoolean(KEY_SEEDED, true).apply();
    }

    private static List<Walk> walks(long now) {
        List<Walk> walks = new ArrayList<>();
        walks.add(new Walk("sample-carlton-gardens",
                "Carlton Gardens loop",
                "Carlton, Australia",
                now - TimeUnit.DAYS.toMillis(3) - TimeUnit.HOURS.toMillis(5),
                TimeUnit.MINUTES.toMillis(52),
                "CLEAR", 19.0,
                new String[]{
                        "Royal_exhibition_building_tulips_straight.jpg",
                        "French_Fountain,_Carlton_Gardens-Horizontal.jpg",
                        "Melbourne_museum_exterior_panorama.jpg"},
                new double[][]{
                        {-37.8073, 144.9700}, {-37.8062, 144.9709}, {-37.8053, 144.9717},
                        {-37.8046, 144.9717}, {-37.8038, 144.9722}, {-37.8033, 144.9717},
                        {-37.8028, 144.9700}, {-37.8040, 144.9690}, {-37.8055, 144.9688},
                        {-37.8068, 144.9693}, {-37.8073, 144.9700}},
                new Stop[]{
                        new Stop("royal-exhibition-building", "Royal Exhibition Building", 14),
                        new Stop("melbourne-museum", "Melbourne Museum", 31)}));
        walks.add(new Walk("sample-southbank-dusk",
                "Southbank at dusk",
                "Southbank, Australia",
                now - TimeUnit.DAYS.toMillis(9) - TimeUnit.HOURS.toMillis(2),
                TimeUnit.MINUTES.toMillis(68),
                "CLOUDY", 15.0,
                new String[]{
                        "Southbank_and_Yarra_River,_Melbourne,_as_seen_from_Evan_Walker_Bridge_20230219_1.jpg",
                        "State_Theatre_Melbourne_dusk_view_201708.jpg",
                        "Flinders_Street_Station_in_Melbourne_at_night,_2023.jpg"},
                new double[][]{
                        {-37.8226, 144.9640}, {-37.8218, 144.9660}, {-37.8210, 144.9676},
                        {-37.8202, 144.9686}, {-37.8199, 144.9678}, {-37.8194, 144.9672},
                        {-37.8187, 144.9668}, {-37.8183, 144.9674}, {-37.8180, 144.9682}},
                new Stop[]{}));
        walks.add(new Walk("sample-parkville-campus",
                "Parkville campus wander",
                "Parkville, Australia",
                now - TimeUnit.DAYS.toMillis(16) - TimeUnit.HOURS.toMillis(7),
                TimeUnit.MINUTES.toMillis(37),
                "CLEAR", 22.0,
                new String[]{
                        "Old_Arts_Building_University_of_Melbourne_2018.jpg",
                        "Ian_Potter_Museum_of_Art_2010.jpg"},
                new double[][]{
                        {-37.7963, 144.9614}, {-37.7970, 144.9605}, {-37.7981, 144.9591},
                        {-37.7988, 144.9598}, {-37.7985, 144.9612}, {-37.7975, 144.9640},
                        {-37.7968, 144.9645}},
                new Stop[]{
                        new Stop("old-arts", "Old Arts Building", 9),
                        new Stop("potter-museum", "Ian Potter Museum of Art", 28)}));
        return walks;
    }

    private static void insert(StoryStepDatabase database, Walk walk) {
        List<RoutePointEntity> points = new ArrayList<>();
        double metres = 0;
        GeoPoint previous = null;
        int sequence = 0;
        // Points every twenty-odd metres along the legs, so the route draws as a line and not a
        // handful of corners; timestamps spread evenly across the walk.
        List<GeoPoint> path = new ArrayList<>();
        for (double[] corner : walk.corners) {
            GeoPoint next = new GeoPoint(corner[0], corner[1]);
            if (previous != null) {
                double leg = GeoCalculator.distanceMeters(previous, next);
                int steps = Math.max(1, (int) Math.round(leg / 22.0));
                for (int s = 1; s <= steps; s++) {
                    double t = (double) s / steps;
                    path.add(new GeoPoint(
                            previous.getLatitude() + (next.getLatitude() - previous.getLatitude()) * t,
                            previous.getLongitude() + (next.getLongitude() - previous.getLongitude()) * t));
                }
                metres += leg;
            } else {
                path.add(next);
            }
            previous = next;
        }
        for (GeoPoint at : path) {
            RoutePointEntity point = new RoutePointEntity();
            point.sessionId = walk.id;
            point.sequence = sequence;
            point.segment = 1;
            point.latitude = at.getLatitude();
            point.longitude = at.getLongitude();
            point.accuracyMeters = 8f;
            point.wallClockMillis = walk.start + walk.active * sequence / Math.max(1, path.size() - 1);
            points.add(point);
            sequence++;
        }

        SessionEntity session = new SessionEntity();
        session.id = walk.id;
        session.state = "COMPLETED";
        session.startWallClockMillis = walk.start;
        session.endWallClockMillis = walk.start + walk.active;
        session.activeDurationMillis = walk.active;
        session.distanceMeters = metres;
        session.segment = 1;
        session.segmentCount = 1;
        session.finished = true;
        session.lastCheckpointWallClockMillis = session.endWallClockMillis;
        session.weatherAreaKey = walk.placeName;
        session.weatherCondition = walk.weather;
        session.weatherTemperatureCelsius = walk.temperature;
        session.weatherObservedAtMillis = walk.start;
        session.weatherFetchedAtMillis = walk.start;
        session.movementState = "MOVING";
        session.movementMovingMillis = walk.active * 4 / 5;
        session.movementStationaryMillis = walk.active - session.movementMovingMillis;
        session.title = walk.title;
        session.placeName = walk.placeName;
        StringBuilder photos = new StringBuilder();
        for (String file : walk.photos) {
            if (photos.length() > 0) {
                photos.append('\n');
            }
            photos.append(COMMONS).append(file.replace(' ', '_')).append(SCALE);
        }
        session.photoFiles = photos.toString();
        database.sessionDao().insert(session);
        database.routePointDao().insertAll(points);
        for (Stop stop : walk.stops) {
            VisitEntity visit = new VisitEntity();
            visit.sessionId = walk.id;
            visit.attractionId = stop.attractionId;
            visit.attractionName = stop.name;
            visit.arrivalWallClockMillis = walk.start + TimeUnit.MINUTES.toMillis(stop.minute);
            visit.unread = false;
            database.visitDao().insert(visit);
        }
    }

    private static final class Walk {
        final String id;
        final String title;
        final String placeName;
        final long start;
        final long active;
        final String weather;
        final double temperature;
        final String[] photos;
        final double[][] corners;
        final Stop[] stops;

        Walk(String id, String title, String placeName, long start, long active, String weather,
             double temperature, String[] photos, double[][] corners, Stop[] stops) {
            this.id = id;
            this.title = title;
            this.placeName = placeName;
            this.start = start;
            this.active = active;
            this.weather = weather;
            this.temperature = temperature;
            this.photos = photos;
            this.corners = corners;
            this.stops = stops;
        }
    }

    private static final class Stop {
        final String attractionId;
        final String name;
        final int minute;

        Stop(String attractionId, String name, int minute) {
            this.attractionId = attractionId;
            this.name = name;
            this.minute = minute;
        }
    }
}
