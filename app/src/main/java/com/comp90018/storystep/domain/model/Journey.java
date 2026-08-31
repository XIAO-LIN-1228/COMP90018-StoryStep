package com.comp90018.storystep.domain.model;

import java.util.Objects;

/** Application-owned summary of a completed foreground exploration session. */
public final class Journey {

    private final String id;
    private final String title;
    private final String dateLabel;
    private final int durationMinutes;
    private final double distanceKilometers;
    private final int storyCount;

    public Journey(String id,
                   String title,
                   String dateLabel,
                   int durationMinutes,
                   double distanceKilometers,
                   int storyCount) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.dateLabel = Objects.requireNonNull(dateLabel, "dateLabel");
        this.durationMinutes = durationMinutes;
        this.distanceKilometers = distanceKilometers;
        this.storyCount = storyCount;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDateLabel() { return dateLabel; }
    public int getDurationMinutes() { return durationMinutes; }
    public double getDistanceKilometers() { return distanceKilometers; }
    public int getStoryCount() { return storyCount; }
}
