package com.comp90018.storystep.ui.forecast;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.DepartureAdvice;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.forecast.SkyCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * One row per departure: when, the chance of rain during the whole walk, its band, and whether
 * the walk runs after dark. Uses the forecast page's row layout.
 */
public class DepartureRowAdapter extends RecyclerView.Adapter<RainRowAdapter.Holder> {

    private final List<DepartureAdvice.Candidate> candidates = new ArrayList<>();
    private int bestIndex = -1;
    private ForecastPlace place = ForecastPlace.unknown();
    @Nullable private HourlyForecast hourly;

    /** Replaces every row: each recomputation is a new set of departures. */
    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<DepartureAdvice.Candidate> rows, int best, ForecastPlace rowsPlace,
                       @Nullable HourlyForecast rowsHourly) {
        place = rowsPlace;
        hourly = rowsHourly;
        candidates.clear();
        candidates.addAll(rows);
        bestIndex = best;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RainRowAdapter.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RainRowAdapter.Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rain_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RainRowAdapter.Holder holder, int position) {
        DepartureAdvice.Candidate candidate = candidates.get(position);
        Resources resources = holder.itemView.getResources();
        holder.time.setText(start(resources, candidate, place));
        holder.icon.setVisibility(View.VISIBLE);
        HourlyForecast.Hour hour = hourly == null ? null : hourly.hourAt(candidate.getStartMillis());
        if (hour != null && hour.getSky() != null && hour.getSky() != SkyCondition.UNKNOWN) {
            // The sky in the hour of departure, day or night by the departure instant itself.
            Boolean day = place.getSun().isDaylight(candidate.getStartMillis());
            holder.icon.setImageResource(ForecastText.icon(hour.getSky(), day));
            holder.icon.setContentDescription(resources.getString(
                    ForecastText.skyDescription(hour.getSky())));
        } else {
            // No hourly forecast: say only whether it is light. A sun here would claim "clear".
            holder.icon.setImageResource(candidate.isDaylight()
                    ? R.drawable.ic_daylight : R.drawable.ic_after_dark);
            holder.icon.setContentDescription(resources.getString(candidate.isDaylight()
                    ? R.string.advice_daylight : R.string.advice_after_dark));
        }
        holder.percent.setText(AdviceText.percent(resources, candidate));
        holder.bar.setProgressCompat(candidate.getPercent(), false);
        String label = resources.getString(AdviceText.band(candidate.getBand()));
        if (!candidate.isDaylight()) {
            label = resources.getString(R.string.advice_label_after_dark, label);
        }
        holder.label.setText(label);
        holder.label.setVisibility(View.VISIBLE);
        holder.itemView.setBackgroundColor(position == bestIndex
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.story_surface_sunk)
                : 0);
    }

    static String start(Resources resources, DepartureAdvice.Candidate candidate,
                        ForecastPlace place) {
        return candidate.isNow()
                ? resources.getString(R.string.advice_now)
                : ForecastText.time(candidate.getStartMillis(), place);
    }

    @Override
    public int getItemCount() {
        return candidates.size();
    }
}
