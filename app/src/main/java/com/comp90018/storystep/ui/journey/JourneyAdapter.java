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
import com.comp90018.storystep.ui.TouchTargets;

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
        private final TextView statDuration;
        private final TextView statDistance;
        private final TextView statStories;
        private final TextView label;
        private final ImageButton delete;
        private final TextView who;
        private final TextView whenWhere;
        private final JourneyPhotoPager photos;

        JourneyViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.journey_item_title);
            date = itemView.findViewById(R.id.journey_item_date);
            stats = itemView.findViewById(R.id.journey_item_stats);
            statDuration = itemView.findViewById(R.id.stat_duration);
            statDistance = itemView.findViewById(R.id.stat_distance);
            statStories = itemView.findViewById(R.id.stat_stories);
            label = itemView.findViewById(R.id.journey_item_label);
            delete = itemView.findViewById(R.id.journey_item_delete);
            // The smallest control in the app, and the destructive one.
            TouchTargets.expand(delete);
            who = itemView.findViewById(R.id.journey_who);
            whenWhere = itemView.findViewById(R.id.journey_when_where);
            photos = itemView.findViewById(R.id.journey_item_photos);
        }

        void bind(SessionHistoryEntry journey,
                  OnJourneySelected selectionListener,
                  OnJourneyDeleteRequested deleteListener) {
            Resources resources = itemView.getResources();

            // The identity line: who, then when and where. The place is only known once the
            // walk is published; until then the destination, if any, stands in.
            String you = resources.getString(R.string.journey_you);
            String went = resources.getString(R.string.journey_went_for_a_walk, you);
            android.text.SpannableString styledWho = new android.text.SpannableString(went);
            styledWho.setSpan(new android.text.style.ForegroundColorSpan(
                            androidx.core.content.ContextCompat.getColor(
                                    itemView.getContext(), R.color.story_signal_ink)),
                    0, you.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            who.setText(styledWho);
            String when = HistoryText.formatDateTime(resources, journey.getStartWallClockMillis());
            String where = journey.getPlaceName() != null ? journey.getPlaceName()
                    : journey.getDestinationName();
            whenWhere.setText(where == null ? when
                    : resources.getString(R.string.journey_when_where, when, where));

            // The photographs the user attached, if any; the card stays compact without them.
            java.util.List<Object> files = new java.util.ArrayList<>();
            for (String name : journey.getPhotoFiles()) {
                files.add(com.comp90018.storystep.platform.media.JourneyPhotoStore.source(
                        itemView.getContext(), journey.getId(), name));
            }
            photos.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
            photos.setPhotos(files);

            // The user's own name for the walk wins; before publishing, the destination stands
            // in, and a session with no chosen destination is still a real walk without a name.
            if (journey.isPublished()) {
                title.setText(journey.getTitle());
            } else if (journey.getDestinationName() == null) {
                title.setText(R.string.history_untitled);
            } else {
                String name = journey.getDestinationName();
                String full = resources.getString(R.string.history_walk_towards, name);
                android.text.SpannableString styled = new android.text.SpannableString(full);
                int start = full.indexOf(name);
                if (start >= 0) {
                    styled.setSpan(new android.text.style.ForegroundColorSpan(
                                    androidx.core.content.ContextCompat.getColor(
                                            itemView.getContext(), R.color.story_signal_ink)),
                            start, start + name.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                title.setText(styled);
            }

            // Started, not ended: it is when the user set out that they remember.
            date.setText(HistoryText.formatDate(resources, journey.getStartWallClockMillis()));

            String duration = HistoryText.formatDuration(resources, journey.getActiveDurationMillis());
            String distance = DistanceText.format(resources, journey.getDistanceMeters());
            stats.setText(resources.getString(R.string.history_stats_format,
                    duration + "  ·  " + distance,
                    HistoryText.formatVisitCount(resources, journey.getVisitCount())));
            statDuration.setText(duration);
            statDistance.setText(distance);
            statStories.setText(String.valueOf(journey.getVisitCount()));

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
