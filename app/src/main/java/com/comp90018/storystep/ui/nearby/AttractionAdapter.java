package com.comp90018.storystep.ui.nearby;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    public AttractionAdapter(OnAttractionSelected listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
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
        holder.bind(getItem(position), listener);
    }

    static class AttractionViewHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView category;
        private final TextView distance;
        private final TextView reason;
        private final ShapeableImageView image;

        AttractionViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.attraction_name);
            category = itemView.findViewById(R.id.attraction_category);
            distance = itemView.findViewById(R.id.attraction_distance);
            reason = itemView.findViewById(R.id.attraction_reason);
            image = itemView.findViewById(R.id.attraction_image);
        }

        void bind(AttractionListItem item, OnAttractionSelected listener) {
            Resources resources = itemView.getResources();
            String categoryLabel = resources.getString(CategoryLabels.labelOf(item.getCategory()));
            String distanceLabel = DistanceText.format(resources, item.getDistanceMeters());

            name.setText(item.getName());
            category.setText(categoryLabel);
            distance.setText(distanceLabel);
            reason.setText(AttractionArtwork.recommendationFor(item.getId()));
            image.setImageResource(AttractionArtwork.drawableFor(item.getId()));
            itemView.setContentDescription(resources.getString(
                    R.string.attraction_item_description,
                    item.getName(),
                    categoryLabel,
                    distanceLabel));
            itemView.setOnClickListener(v -> listener.onAttractionSelected(item));
        }
    }
}
