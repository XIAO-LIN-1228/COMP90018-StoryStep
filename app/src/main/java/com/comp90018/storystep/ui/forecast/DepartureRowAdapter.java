package com.comp90018.storystep.ui.forecast;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.DepartureAdvice;
import com.comp90018.storystep.ui.HistoryText;

import java.util.ArrayList;
import java.util.List;

/**
 * One row per departure: when, the chance of rain during the whole walk, its band, and whether
 * the walk runs after dark. Uses the forecast page's row layout.
 */
public class DepartureRowAdapter extends RecyclerView.Adapter<RainRowAdapter.Holder> {

    private final List<DepartureAdvice.Candidate> candidates = new ArrayList<>();
    private int bestIndex = -1;

    /** Replaces every row: each recomputation is a new set of departures. */
    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<DepartureAdvice.Candidate> rows, int best) {
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
        holder.time.setText(start(resources, candidate));
        // The icon slot marks a walk that runs into the dark; daylight walks leave it empty.
        holder.icon.setVisibility(candidate.isDaylight() ? View.INVISIBLE : View.VISIBLE);
        holder.icon.setImageResource(R.drawable.ic_weather_clear_night);
        holder.icon.setContentDescription(resources.getString(R.string.advice_after_dark));
        holder.percent.setText(resources.getString(R.string.forecast_percent,
                candidate.getPercent()));
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

    static String start(Resources resources, DepartureAdvice.Candidate candidate) {
        return candidate.isNow()
                ? resources.getString(R.string.advice_now)
                : HistoryText.formatTime(candidate.getStartMillis());
    }

    @Override
    public int getItemCount() {
        return candidates.size();
    }
}
