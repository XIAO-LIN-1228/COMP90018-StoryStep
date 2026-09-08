package com.comp90018.storystep.domain.attraction;

/**
 * How much a cached set of attractions can still be trusted.
 *
 * <p>Mirrors {@link com.comp90018.storystep.domain.weather.WeatherFreshness} deliberately: the
 * middle state is the one that matters, because a day-old list of museums is still a perfectly
 * useful list of museums when the network is gone. It only has to be labelled.
 *
 * <p>The thresholds differ from weather's by an order of magnitude, and for a reason. Weather is
 * about now; a museum is where it was yesterday.
 */
public enum AttractionFreshness {

    /** Fetched within the refresh interval. Present it plainly; no request is needed. */
    FRESH,

    /** Past the refresh interval but still worth showing, labelled as cached. */
    STALE,

    /** Old enough that it should be refetched before being shown. */
    EXPIRED;

    /** True when the cached set may be shown, with a label if it is not fresh. */
    public boolean isPresentable() {
        return this != EXPIRED;
    }
}
