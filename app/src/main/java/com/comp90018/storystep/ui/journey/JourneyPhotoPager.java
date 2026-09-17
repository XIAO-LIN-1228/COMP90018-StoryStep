package com.comp90018.storystep.ui.journey;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.comp90018.storystep.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A walk's photographs, one at a time, swiped sideways, with page dots along the bottom.
 * Rounded like a card. Hidden by its host when there are none; with one photo the dots stay
 * away, since there is nothing to page.
 */
public class JourneyPhotoPager extends FrameLayout {

    private final RecyclerView pages;
    private final LinearLayout dots;
    private final PhotoAdapter adapter = new PhotoAdapter();
    private final LinearLayoutManager layout;

    public JourneyPhotoPager(Context context) {
        this(context, null);
    }

    public JourneyPhotoPager(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        pages = new RecyclerView(context);
        layout = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        pages.setLayoutManager(layout);
        pages.setAdapter(adapter);
        new PagerSnapHelper().attachToRecyclerView(pages);
        addView(pages, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams dotParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        dotParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dotParams.bottomMargin = dp(10);
        addView(dots, dotParams);

        pages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                markCurrent();
            }
        });

        float radius = dp(14);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    public void setPhotos(List<File> photos) {
        adapter.setPhotos(photos);
        dots.removeAllViews();
        if (photos.size() > 1) {
            for (int i = 0; i < photos.size(); i++) {
                // An ImageView, not a bare View: it measures to the drawable's own size, where
                // a View under an at-most spec would take everything it is offered.
                ImageView dot = new ImageView(getContext());
                dot.setImageResource(R.drawable.bg_photo_dot);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.setMarginStart(i == 0 ? 0 : dp(4));
                dots.addView(dot, params);
            }
        }
        pages.scrollToPosition(0);
        markCurrent();
    }

    private void markCurrent() {
        int current = layout.findFirstCompletelyVisibleItemPosition();
        if (current == RecyclerView.NO_POSITION) {
            current = layout.findFirstVisibleItemPosition();
        }
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setSelected(i == current);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.Holder> {

        private final List<File> photos = new ArrayList<>();

        void setPhotos(List<File> files) {
            photos.clear();
            photos.addAll(files);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_journey_photo, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Glide.with(holder.image).load(photos.get(position)).centerCrop().into(holder.image);
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;

            Holder(@NonNull View itemView) {
                super(itemView);
                image = (ImageView) itemView;
            }
        }
    }
}
