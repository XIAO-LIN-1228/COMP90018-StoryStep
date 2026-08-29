package com.comp90018.storystep.domain.model;

/**
 * Categories used to classify an attraction. The UI layer maps these to localized labels;
 * the domain layer never holds user-visible text.
 */
public enum AttractionCategory {
    HISTORICAL_BUILDING,
    MUSEUM,
    PARK,
    PUBLIC_ART,
    CULTURAL_SITE,
    UNIVERSITY_LANDMARK
}
