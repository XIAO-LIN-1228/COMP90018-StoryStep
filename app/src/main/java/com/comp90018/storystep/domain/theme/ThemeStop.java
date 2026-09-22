package com.comp90018.storystep.domain.theme;

import com.comp90018.storystep.domain.model.*;
import java.util.*;

/** A curated outdoor observation stop. Questions never stand in for a verified arrival. */
public final class ThemeStop {
    public final String id, name, story, sourceUrl, question;
    public final double latitude, longitude;
    public final List<String> options;
    public final int correctAnswer;

    public ThemeStop(String id, String name, String story, String sourceUrl,
            double latitude, double longitude, String question, List<String> options, int correctAnswer) {
        this.id = required(id); this.name = required(name); this.story = required(story);
        this.sourceUrl = required(sourceUrl); this.question = required(question);
        new GeoPoint(latitude, longitude);
        if (!sourceUrl.startsWith("https://") || options == null || options.size() < 2
                || correctAnswer < 0 || correctAnswer >= options.size()) {
            throw new IllegalArgumentException("invalid stop content");
        }
        List<String> copy = new ArrayList<>();
        for (String option : options) { copy.add(required(option)); }
        this.options = Collections.unmodifiableList(copy);
        this.correctAnswer = correctAnswer;
        this.latitude = latitude; this.longitude = longitude;
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("missing content"); }
        return value;
    }

    public Attraction attraction() {
        return Attraction.seeded(id, name, AttractionCategory.UNIVERSITY_LANDMARK, story,
                IndoorClassification.OUTDOOR, new GeoPoint(latitude, longitude));
    }
}
