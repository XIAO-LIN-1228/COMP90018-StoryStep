package com.comp90018.storystep.ui.forecast;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * One row per hour: interval, condition icon, chance of rain and a bar of that length. The row
 * layout is shared with departure advice, whose rows also carry a label.
 */
public class RainRowAdapter extends RecyclerView.Adapter<RainRowAdapter.Holder> {

    private final List<HourlyForecast.Hour> hours = new ArrayList<>();
    private ForecastPlace place = ForecastPlace.unknown();

    /** Replaces every row: each load is a new forecast, so there is nothing finer to report. */
    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<HourlyForecast.Hour> rows, ForecastPlace rowsPlace) {
        place = rowsPlace;
        hours.clear();
        hours.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rain_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HourlyForecast.Hour hour = hours.get(position);
        View item = holder.itemView;
        holder.time.setText(ForecastText.interval(item.getResources(),
                hour.getStartMillis(), hour.getEndMillis(), place));
        holder.icon.setImageResource(ForecastText.icon(hour.getCondition(), hour.getDaylight()));
        holder.icon.setContentDescription(item.getResources().getString(
                ForecastText.conditionDescription(hour.getCondition())));
        Integer chance = hour.getPrecipitationProbability();
        holder.percent.setText(chance == null
                ? item.getResources().getString(R.string.forecast_missing)
                : item.getResources().getString(R.string.forecast_percent, chance));
        holder.bar.setProgressCompat(chance == null ? 0 : chance, false);
        holder.label.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return hours.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView time;
        final ImageView icon;
        final TextView percent;
        final LinearProgressIndicator bar;
        final TextView label;

        Holder(View view) {
            super(view);
            time = view.findViewById(R.id.rain_row_time);
            icon = view.findViewById(R.id.rain_row_icon);
            percent = view.findViewById(R.id.rain_row_percent);
            bar = view.findViewById(R.id.rain_row_bar);
            label = view.findViewById(R.id.rain_row_label);
        }
    }
}
