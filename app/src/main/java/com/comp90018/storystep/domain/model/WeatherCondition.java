package com.comp90018.storystep.domain.model;

/**
 * The weather categories the app is willing to distinguish.
 *
 * <p>Deliberately coarse. The app uses weather for one purpose — preferring indoor places when it
 * is raining — so a forecast vocabulary of dozens of codes is collapsed into the few categories
 * that actually change a decision.
 *
 * <p>{@link #UNKNOWN} is a real category and must never be collapsed into {@link #CLEAR}. A
 * provider code this app does not recognise is not evidence of good weather, and ranking treats
 * unknown as neutral rather than as dry.
 */
public enum WeatherCondition {

    /** Clear or mostly clear sky. */
    CLEAR,

    /** Overcast, fog or haze: dry, but not sunny. */
    CLOUDY,

    /** Any liquid precipitation, including drizzle, showers and thunderstorms. */
    RAIN,

    /** Any frozen precipitation, including sleet. */
    SNOW,

    /** Reported, but not a category this app recognises. Neutral, never treated as dry. */
    UNKNOWN;

    /**
     * True when the user would want to be indoors.
     *
     * <p>{@link #UNKNOWN} answers false, which is not a claim that it is dry: callers that need to
     * distinguish "known dry" from "unknown" must test the constant, not this method.
     */
    public boolean isWet() {
        return this == RAIN || this == SNOW;
    }

    /** True when the condition reflects an actual observation this app understood. */
    public boolean isKnown() {
        return this != UNKNOWN;
    }
}
