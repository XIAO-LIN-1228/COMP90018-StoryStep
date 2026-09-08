package com.comp90018.storystep.domain.model;

/**
 * Whether an attraction is experienced indoors, outdoors, or neither is known.
 *
 * <p>Three states rather than a boolean, because a real provider frequently says nothing. A
 * fetched place with no {@code building}, {@code indoor} or {@code leisure} tag is not outdoors —
 * it is unclassified, and collapsing that into {@code false} would make weather-aware ranking
 * recommend a museum out into the rain on the strength of a missing tag.
 *
 * <p>REQ-14 deferred this tri-state to REQ-18 for exactly that reason: it only becomes observable
 * once records stop being hand-written.
 */
public enum IndoorClassification {

    /** Primarily experienced under cover. */
    INDOOR,

    /** Primarily experienced in the open. */
    OUTDOOR,

    /** The source said nothing usable. Contextual ranking must treat this as neutral. */
    UNKNOWN;

    /** True only for {@link #INDOOR}; {@link #UNKNOWN} is not a weaker yes. */
    public boolean isIndoor() {
        return this == INDOOR;
    }

    /** True only for {@link #OUTDOOR}; {@link #UNKNOWN} is not a weaker no. */
    public boolean isOutdoor() {
        return this == OUTDOOR;
    }
}
