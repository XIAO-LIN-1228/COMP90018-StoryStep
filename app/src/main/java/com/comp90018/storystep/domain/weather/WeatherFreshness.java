package com.comp90018.storystep.domain.weather;

/**
 * How much a stored weather observation can still be trusted.
 *
 * <p>Three levels rather than a boolean, because the middle one carries the whole point: an
 * observation past its refresh interval is still worth showing when the network is unavailable —
 * it just has to be labelled, and it must stop influencing anything that assumes current
 * conditions.
 */
public enum WeatherFreshness {

    /** Within the refresh interval. Present it plainly; no refresh is needed. */
    FRESH,

    /** Past the refresh interval but still worth displaying, labelled with its age. */
    STALE,

    /** Old enough that presenting it would mislead. Treat as no observation at all. */
    EXPIRED;

    /** True when the snapshot may be shown to the user, with a label if it is not fresh. */
    public boolean isPresentable() {
        return this != EXPIRED;
    }

    /**
     * True when the snapshot may be used as a current-conditions input, for example by ranking.
     *
     * <p>Only {@link #FRESH} qualifies. Stale weather is displayable but must score as neutral,
     * because "it was raining forty minutes ago" is not a reason to reorder what is nearby now.
     */
    public boolean isUsableAsContext() {
        return this == FRESH;
    }
}
