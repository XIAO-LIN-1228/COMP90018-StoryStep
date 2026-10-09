package com.comp90018.storystep.domain.forecast;

/**
 * Plain-language bands for a chance of rain, aligned to US National Weather Service terms
 * (30-50% "chance", 60-70% "likely"). The labels themselves live in the UI.
 */
public enum RainBand {

    /** 0-29%. */
    LOW,

    /** 30-59%: worth carrying an umbrella. */
    POSSIBLE,

    /** 60-79%: the "not advisable" threshold starts here. */
    LIKELY,

    /** 80-100%. */
    EXPECTED;

    /** Percent at and above which a departure is not advised. */
    public static final int NOT_ADVISABLE_PERCENT = 60;

    public static RainBand forPercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent out of range: " + percent);
        }
        if (percent >= 80) {
            return EXPECTED;
        }
        if (percent >= NOT_ADVISABLE_PERCENT) {
            return LIKELY;
        }
        if (percent >= 30) {
            return POSSIBLE;
        }
        return LOW;
    }
}
