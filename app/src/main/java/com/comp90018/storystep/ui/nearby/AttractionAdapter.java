package com.comp90018.storystep.ui.nearby;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.AttractionArtwork;
import com.comp90018.storystep.ui.CategoryLabels;
import com.comp90018.storystep.ui.DistanceText;
import com.google.android.material.imageview.ShapeableImageView;

/** Binds {@link AttractionListItem}s to the nearby list rows. */
public class AttractionAdapter
        extends ListAdapter<AttractionListItem, AttractionAdapter.AttractionViewHolder> {

    /** Notified when a row is activated, so navigation stays in the Fragment. */
    public interface OnAttractionSelected {
        void onAttractionSelected(AttractionListItem item);
    }

    /**
     * Notified when a row's bookmark is pressed.
     *
     * <p>Separate from {@link OnAttractionSelected} because the two are different intentions:
     * opening a place and keeping one. The adapter reports the press and nothing else — the
     * ViewModel owns the write, and the new state arrives as a fresh list.
     */
    public interface OnSaveToggled {
        void onSaveToggled(AttractionListItem item);
    }

    private static final DiffUtil.ItemCallback<AttractionListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AttractionListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull AttractionListItem oldItem,
                                               @NonNull AttractionListItem newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AttractionListItem oldItem,
                                                  @NonNull AttractionListItem newItem) {
                    return oldItem.equals(newItem);
                }
            };

    private final OnAttractionSelected listener;
    @Nullable
    private final OnSaveToggled saveListener;

    public AttractionAdapter(OnAttractionSelected listener) {
        this(listener, null);
    }

    /**
     * @param saveListener notified about bookmark presses, or null to show the bookmark as a
     *                     read-only indicator
     */
    public AttractionAdapter(OnAttractionSelected listener, @Nullable OnSaveToggled saveListener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        this.saveListener = saveListener;
    }

    @NonNull
    @Override
    public AttractionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attraction, parent, false);
        return new AttractionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttractionViewHolder holder, int position) {
        holder.bind(getItem(position), listener, saveListener);
    }

    static class AttractionViewHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView category;
        private final TextView distance;
        private final TextView reason;
        private final ShapeableImageView image;
        private final ImageButton saveButton;

        AttractionViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.attraction_name);
            category = itemView.findViewById(R.id.attraction_category);
            distance = itemView.findViewById(R.id.attraction_distance);
            reason = itemView.findViewById(R.id.attraction_reason);
            image = itemView.findViewById(R.id.attraction_image);
            saveButton = itemView.findViewById(R.id.attraction_save_button);
        }

        void bind(AttractionListItem item,
                  OnAttractionSelected listener,
                  @Nullable OnSaveToggled saveListener) {
            Resources resources = itemView.getResources();
            String categoryLabel = resources.getString(CategoryLabels.labelOf(item.getCategory()));
            String distanceLabel = DistanceText.format(resources, item.getDistanceMeters());

            name.setText(item.getName());
            category.setText(categoryLabel);
            distance.setText(distanceLabel);
            // A sentence or two: the place's own description first, the bundled recommendation
            // line for a seed place without one, nothing when there is neither.
            String intro = item.getDescription();
            if (intro.isEmpty()) {
                String recommendation = AttractionArtwork.recommendationFor(item.getId());
                intro = recommendation == null ? "" : recommendation;
            }
            reason.setText(intro);
            reason.setVisibility(intro.isEmpty() ? View.GONE : View.VISIBLE);
            // A fetched photograph when the source has one; the bundled artwork or the placeholder
            // otherwise. Glide caches to disk, so a row scrolled back into view costs nothing.
            int fallback = AttractionArtwork.drawableFor(item.getId());
            if (item.getImageUrl() != null) {
                com.bumptech.glide.Glide.with(image)
                        .load(item.getImageUrl())
                        .placeholder(fallback)
                        .error(fallback)
                        .override(320, 320)
                        .centerCrop()
                        .into(image);
            } else {
                com.bumptech.glide.Glide.with(image).clear(image);
                image.setImageResource(fallback);
            }
            itemView.setContentDescription(resources.getString(
                    R.string.attraction_item_description,
                    item.getName(),
                    categoryLabel,
                    distanceLabel));
            itemView.setOnClickListener(v -> listener.onAttractionSelected(item));

            saveButton.setImageResource(item.isSaved()
                    ? R.drawable.ic_bookmark_filled
                    : R.drawable.ic_bookmark_outline);
            saveButton.setContentDescription(resources.getString(item.isSaved()
                    ? R.string.attraction_unsave
                    : R.string.attraction_save));
            saveButton.setEnabled(saveListener != null);
            // Without a listener the bookmark is a state indicator, not a control, so it must not
            // swallow the tap that would otherwise open the row.
            saveButton.setClickable(saveListener != null);
            saveButton.setOnClickListener(
                    saveListener == null ? null : v -> saveListener.onSaveToggled(item));
        }
    }
}
