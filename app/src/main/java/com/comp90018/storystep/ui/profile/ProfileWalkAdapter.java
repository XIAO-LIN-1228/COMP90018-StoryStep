package com.comp90018.storystep.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.platform.media.JourneyPhotoStore;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.HistoryText;

/** The profile's strip of recent walks: a photograph each, the name and the figures over it. */
final class ProfileWalkAdapter extends ListAdapter<SessionHistoryEntry, ProfileWalkAdapter.Holder> {

    interface OnWalkSelected {
        void onWalkSelected(SessionHistoryEntry walk);
    }

    private static final DiffUtil.ItemCallback<SessionHistoryEntry> DIFF =
            new DiffUtil.ItemCallback<SessionHistoryEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull SessionHistoryEntry a,
                                               @NonNull SessionHistoryEntry b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SessionHistoryEntry a,
                                                  @NonNull SessionHistoryEntry b) {
                    return a.equals(b);
                }
            };

    private final OnWalkSelected listener;

    ProfileWalkAdapter(OnWalkSelected listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_walk, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SessionHistoryEntry walk = getItem(position);
        holder.title.setText(walk.isPublished() ? walk.getTitle()
                : walk.getDestinationName() != null ? walk.getDestinationName()
                : holder.itemView.getResources().getString(R.string.history_untitled));
        holder.meta.setText(HistoryText.formatDate(holder.itemView.getResources(),
                walk.getStartWallClockMillis()) + " · "
                + DistanceText.format(holder.itemView.getResources(), walk.getDistanceMeters()));
        if (walk.getPhotoFiles().isEmpty()) {
            Glide.with(holder.photo).clear(holder.photo);
            holder.photo.setImageResource(R.drawable.bg_thumb_placeholder);
        } else {
            Glide.with(holder.photo)
                    .load(JourneyPhotoStore.source(holder.itemView.getContext(), walk.getId(),
                            walk.getPhotoFiles().get(0)))
                    .centerCrop()
                    .into(holder.photo);
        }
        holder.itemView.setOnClickListener(v -> listener.onWalkSelected(walk));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView title;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.profile_walk_photo);
            title = itemView.findViewById(R.id.profile_walk_title);
            meta = itemView.findViewById(R.id.profile_walk_meta);
        }
    }
}
