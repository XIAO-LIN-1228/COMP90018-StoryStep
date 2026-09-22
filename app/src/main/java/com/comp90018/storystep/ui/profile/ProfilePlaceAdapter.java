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
import com.comp90018.storystep.domain.model.SavedAttraction;
import com.comp90018.storystep.ui.AttractionArtwork;

/** The profile's strip of kept places: a square photograph each, the name beneath. */
final class ProfilePlaceAdapter extends ListAdapter<SavedAttraction, ProfilePlaceAdapter.Holder> {

    interface OnPlaceSelected {
        void onPlaceSelected(SavedAttraction place);
    }

    private static final DiffUtil.ItemCallback<SavedAttraction> DIFF =
            new DiffUtil.ItemCallback<SavedAttraction>() {
                @Override
                public boolean areItemsTheSame(@NonNull SavedAttraction a, @NonNull SavedAttraction b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SavedAttraction a, @NonNull SavedAttraction b) {
                    return a.equals(b);
                }
            };

    private final OnPlaceSelected listener;

    ProfilePlaceAdapter(OnPlaceSelected listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_place, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SavedAttraction place = getItem(position);
        holder.name.setText(place.getAttraction().getName());
        int fallback = AttractionArtwork.drawableFor(place.getId());
        String url = place.getAttraction().getImageUrl();
        if (url == null) {
            Glide.with(holder.photo).clear(holder.photo);
            holder.photo.setImageResource(fallback);
        } else {
            Glide.with(holder.photo).load(url).placeholder(fallback).error(fallback)
                    .override(400, 400).centerCrop().into(holder.photo);
        }
        holder.itemView.setOnClickListener(v -> listener.onPlaceSelected(place));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;

        Holder(@NonNull View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.profile_place_photo);
            name = itemView.findViewById(R.id.profile_place_name);
        }
    }
}
