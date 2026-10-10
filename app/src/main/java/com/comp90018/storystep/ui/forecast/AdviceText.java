package com.comp90018.storystep.ui.forecast;

import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.RainBand;

import java.util.List;

/** Words for departure advice. */
final class AdviceText {

    private AdviceText() {
    }

    @StringRes
    static int band(RainBand band) {
        switch (band) {
            case EXPECTED:
                return R.string.advice_band_expected;
            case LIKELY:
                return R.string.advice_band_likely;
            case POSSIBLE:
                return R.string.advice_band_possible;
            case LOW:
            default:
                return R.string.advice_band_low;
        }
    }

    /** "ICON", "ICON and GFS", "ICON, ECMWF and GFS". */
    static String models(List<String> names, String and) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.size() - 1; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(names.get(i));
        }
        return text.append(' ').append(and).append(' ')
                .append(names.get(names.size() - 1)).toString();
    }
}
