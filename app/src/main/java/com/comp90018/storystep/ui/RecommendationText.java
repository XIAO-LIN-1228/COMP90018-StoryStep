package com.comp90018.storystep.ui;

import android.content.res.Resources;
import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.recommendation.RecommendationEngine.Reason;
import java.util.ArrayList;
import java.util.List;

/** Localized explanations of the components actually used in the score. */
public final class RecommendationText {
    private RecommendationText() { }

    public static String format(Resources resources, List<Reason> reasons, double distance) {
        List<String> labels = new ArrayList<>();
        for (Reason reason : reasons) {
            switch (reason) {
                case DISTANCE:
                    labels.add(resources.getString(R.string.recommendation_distance,
                            DistanceText.format(resources, distance)));
                    break;
                case INDOOR_WET: labels.add(resources.getString(R.string.recommendation_wet)); break;
                case OUTDOOR_DAY: labels.add(resources.getString(R.string.recommendation_day)); break;
                case INDOOR_EVENING: labels.add(resources.getString(R.string.recommendation_evening)); break;
                case SAVED: labels.add(resources.getString(R.string.recommendation_saved)); break;
                case UNVISITED: labels.add(resources.getString(R.string.recommendation_unvisited)); break;
                case OPEN_NOW: labels.add(resources.getString(R.string.recommendation_open)); break;
                case CLOSED_NOW: labels.add(resources.getString(R.string.recommendation_closed)); break;
                case POPULAR: labels.add(resources.getString(R.string.recommendation_popular)); break;
            }
        }
        return String.join(" · ", labels);
    }
}
