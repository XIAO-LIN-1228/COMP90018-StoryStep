package com.comp90018.storystep.ui.plan;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.ui.CategoryLabels;
import com.comp90018.storystep.ui.DistanceText;

import java.util.ArrayList;
import java.util.List;

/**
 * The planned stops in order, each with the leg that reaches it.
 *
 * <p>A plain adapter rather than a {@code ListAdapter}: every row shows its own position and the
 * distance from the row above, so moving one stop changes the text of its neighbours too, and
 * diffing by item would have to be told that anyway.
 */
final class PlanStopAdapter extends RecyclerView.Adapter<PlanStopAdapter.Holder> {

    interface OnStopAction {
        void onMove(String attractionId, int by);

        void onRemove(String attractionId);

        void onOpen(Attraction stop);
    }

    private final List<Attraction> stops = new ArrayList<>();
    private final OnStopAction listener;
    private GeoPoint origin;

    PlanStopAdapter(OnStopAction listener) {
        this.listener = listener;
    }

    void setStops(List<Attraction> next, GeoPoint origin) {
        this.origin = origin;
        stops.clear();
        stops.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plan_stop, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Attraction stop = stops.get(position);
        Resources resources = holder.itemView.getResources();
        holder.order.setText(String.valueOf(position + 1));
        holder.name.setText(stop.getName());

        GeoPoint from = position == 0 ? origin : stops.get(position - 1).getLocation();
        String category = resources.getString(CategoryLabels.labelOf(stop.getCategory()));
        if (from == null) {
            holder.meta.setText(category);
        } else {
            String leg = DistanceText.format(resources,
                    GeoCalculator.distanceMeters(from, stop.getLocation()));
            holder.meta.setText(resources.getString(position == 0
                    ? R.string.plan_first_leg : R.string.plan_next_leg, leg) + " · " + category);
        }

        holder.up.setEnabled(position > 0);
        holder.up.setAlpha(position > 0 ? 1f : 0.3f);
        holder.down.setEnabled(position < stops.size() - 1);
        holder.down.setAlpha(position < stops.size() - 1 ? 1f : 0.3f);
        holder.up.setOnClickListener(v -> listener.onMove(stop.getId(), -1));
        holder.down.setOnClickListener(v -> listener.onMove(stop.getId(), 1));
        holder.remove.setOnClickListener(v -> listener.onRemove(stop.getId()));
        holder.itemView.setOnClickListener(v -> listener.onOpen(stop));
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView order;
        final TextView name;
        final TextView meta;
        final ImageButton up;
        final ImageButton down;
        final ImageButton remove;

        Holder(@NonNull View itemView) {
            super(itemView);
            order = itemView.findViewById(R.id.plan_stop_order);
            name = itemView.findViewById(R.id.plan_stop_name);
            meta = itemView.findViewById(R.id.plan_stop_meta);
            up = itemView.findViewById(R.id.plan_stop_up);
            down = itemView.findViewById(R.id.plan_stop_down);
            remove = itemView.findViewById(R.id.plan_stop_remove);
        }
    }
}
