package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * The practical facts about a place: when it is open, how to reach it, whether you can get in.
 *
 * <p>Every field is optional and usually absent - OpenStreetMap records these for a museum far
 * more often than for a sculpture - so this is a small immutable bundle rather than five more
 * fields on {@link Attraction}, and {@link #NONE} is what most places have.
 *
 * <p>The strings are kept exactly as the source recorded them. Opening hours in particular are
 * written in OpenStreetMap's own syntax; tidying them for reading is the UI's job, and inventing
 * an answer to "is it open right now?" from a syntax this app does not fully parse would be
 * worse than showing the hours and letting the reader decide.
 */
public final class PlaceFacts {

    /** What a place has when its source recorded none of this, which is most places. */
    public static final PlaceFacts NONE = new PlaceFacts(null, null, null, null, null);

    private final String openingHours;
    private final String phone;
    private final String website;
    private final String wheelchair;
    private final String fee;

    public PlaceFacts(String openingHours, String phone, String website, String wheelchair,
                      String fee) {
        this.openingHours = trimmed(openingHours);
        this.phone = trimmed(phone);
        this.website = trimmed(website);
        this.wheelchair = trimmed(wheelchair);
        this.fee = trimmed(fee);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** True when there is nothing to show, so the UI can leave the section out entirely. */
    public boolean isEmpty() {
        return openingHours == null && phone == null && website == null
                && wheelchair == null && fee == null;
    }

    /** Opening hours in OpenStreetMap's syntax, or null. */
    public String getOpeningHours() {
        return openingHours;
    }

    public String getPhone() {
        return phone;
    }

    /** An absolute URL, or null. */
    public String getWebsite() {
        return website;
    }

    /** {@code yes}, {@code limited}, {@code no} or null - OpenStreetMap's own vocabulary. */
    public String getWheelchair() {
        return wheelchair;
    }

    /** {@code yes}, {@code no}, or a description of the charge; null when unrecorded. */
    public String getFee() {
        return fee;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlaceFacts)) {
            return false;
        }
        PlaceFacts that = (PlaceFacts) other;
        return Objects.equals(openingHours, that.openingHours)
                && Objects.equals(phone, that.phone)
                && Objects.equals(website, that.website)
                && Objects.equals(wheelchair, that.wheelchair)
                && Objects.equals(fee, that.fee);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openingHours, phone, website, wheelchair, fee);
    }
}
