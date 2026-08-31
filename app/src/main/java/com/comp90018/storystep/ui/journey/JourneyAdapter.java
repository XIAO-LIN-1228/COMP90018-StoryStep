package com.comp90018.storystep.ui.journey;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.Journey;
import com.comp90018.storystep.ui.widget.StoryMapView;

import java.util.Locale;

/** Binds backend-independent journey summaries to the history list. */
public final class JourneyAdapter
        extends ListAdapter<Journey, JourneyAdapter.JourneyViewHolder> {

    public interface OnJourneySelected { void onJourneySelected(Journey journey); }

    private static final DiffUtil.ItemCallback<Journey> DIFF =
            new DiffUtil.ItemCallback<Journey>() {
                @Override public boolean areItemsTheSame(@NonNull Journey oldItem,
                                                         @NonNull Journey newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override public boolean areContentsTheSame(@NonNull Journey oldItem,
                                                            @NonNull Journey newItem) {
                    return oldItem.getTitle().equals(newItem.getTitle())
                            && oldItem.getDateLabel().equals(newItem.getDateLabel())
                            && oldItem.getDurationMinutes() == newItem.getDurationMinutes()
                            && oldItem.getDistanceKilometers() == newItem.getDistanceKilometers()
                            && oldItem.getStoryCount() == newItem.getStoryCount();
                }
            };

    private final OnJourneySelected listener;

    public JourneyAdapter(OnJourneySelected listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public JourneyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new JourneyViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journey, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull JourneyViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static final class JourneyViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView date;
        private final TextView stats;
        private final StoryMapView map;

        JourneyViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.journey_item_title);
            date = itemView.findViewById(R.id.journey_item_date);
            stats = itemView.findViewById(R.id.journey_item_stats);
            map = itemView.findViewById(R.id.journey_map_thumbnail);
            map.setExplorationMode(true);
            map.setCompactMode(true);
        }

        void bind(Journey journey, OnJourneySelected listener) {
            title.setText(journey.getTitle());
            date.setText(journey.getDateLabel());
            stats.setText(String.format(Locale.getDefault(), "%d min  ·  %.1f km\n%d stories discovered",
                    journey.getDurationMinutes(), journey.getDistanceKilometers(),
                    journey.getStoryCount()));
            itemView.setOnClickListener(v -> listener.onJourneySelected(journey));
        }
    }
}
