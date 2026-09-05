package com.comp90018.storystep.ui.journey;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.HistoryText;

/** Binds recorded sessions to the history list. */
public final class JourneyAdapter
        extends ListAdapter<SessionHistoryEntry, JourneyAdapter.JourneyViewHolder> {

    public interface OnJourneySelected {
        void onJourneySelected(SessionHistoryEntry journey);
    }

    public interface OnJourneyDeleteRequested {
        void onJourneyDeleteRequested(SessionHistoryEntry journey);
    }

    private static final DiffUtil.ItemCallback<SessionHistoryEntry> DIFF =
            new DiffUtil.ItemCallback<SessionHistoryEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull SessionHistoryEntry oldItem,
                                               @NonNull SessionHistoryEntry newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SessionHistoryEntry oldItem,
                                                  @NonNull SessionHistoryEntry newItem) {
                    // The model's own equality already covers every field the row renders,
                    // including the two labels a totals-only comparison would miss.
                    return oldItem.equals(newItem);
                }
            };

    private final OnJourneySelected selectionListener;
    private final OnJourneyDeleteRequested deleteListener;

    public JourneyAdapter(OnJourneySelected selectionListener,
                          OnJourneyDeleteRequested deleteListener) {
        super(DIFF);
        this.selectionListener = selectionListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public JourneyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new JourneyViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journey, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull JourneyViewHolder holder, int position) {
        holder.bind(getItem(position), selectionListener, deleteListener);
    }

    static final class JourneyViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView date;
        private final TextView stats;
        private final TextView label;
        private final ImageButton delete;

        JourneyViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.journey_item_title);
            date = itemView.findViewById(R.id.journey_item_date);
            stats = itemView.findViewById(R.id.journey_item_stats);
            label = itemView.findViewById(R.id.journey_item_label);
            delete = itemView.findViewById(R.id.journey_item_delete);
        }

        void bind(SessionHistoryEntry journey,
                  OnJourneySelected selectionListener,
                  OnJourneyDeleteRequested deleteListener) {
            Resources resources = itemView.getResources();

            // A session with no chosen destination is still a real walk; it just has no name.
            title.setText(journey.getDestinationName() == null
                    ? resources.getString(R.string.history_untitled)
                    : resources.getString(R.string.history_walk_towards,
                            journey.getDestinationName()));

            // Started, not ended: it is when the user set out that they remember.
            date.setText(HistoryText.formatDate(resources, journey.getStartWallClockMillis()));

            stats.setText(resources.getString(R.string.history_stats_format,
                    HistoryText.formatDuration(resources, journey.getActiveDurationMillis())
                            + "  ·  "
                            + DistanceText.format(resources, journey.getDistanceMeters()),
                    HistoryText.formatVisitCount(resources, journey.getVisitCount())));

            // The two honesty labels. Interrupted is the more serious of the two, so it wins the
            // one line available rather than both being crammed in.
            if (journey.isInterrupted()) {
                label.setVisibility(View.VISIBLE);
                label.setText(R.string.history_interrupted);
            } else if (journey.hasRouteGaps()) {
                label.setVisibility(View.VISIBLE);
                label.setText(resources.getString(
                        R.string.history_has_gaps, journey.getSegmentCount()));
            } else {
                label.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> selectionListener.onJourneySelected(journey));
            delete.setOnClickListener(v -> deleteListener.onJourneyDeleteRequested(journey));
        }
    }
}
