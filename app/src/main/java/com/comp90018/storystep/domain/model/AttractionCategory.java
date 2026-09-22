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
    UNIVERSITY_LANDMARK,
    /** Somewhere to eat: a restaurant, a food court. */
    FOOD,
    /** Somewhere to drink: a cafe, a bar, a pub. */
    DRINK,
    /** Somewhere to shop: a market, an arcade, a department store. */
    SHOPPING;

    /** The broad kind a category belongs to, which is what the map's chips filter by. */
    public Kind kind() {
        switch (this) {
            case FOOD:
                return Kind.FOOD;
            case DRINK:
                return Kind.DRINK;
            case SHOPPING:
                return Kind.SHOPPING;
            default:
                return Kind.SIGHTS;
        }
    }

    /** Sights, food, drink or shopping: the four kinds of place the map can show or hide. */
    public enum Kind { SIGHTS, FOOD, DRINK, SHOPPING }
}
