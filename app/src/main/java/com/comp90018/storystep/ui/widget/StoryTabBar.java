package com.comp90018.storystep.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.comp90018.storystep.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The tab bar, drawn by hand.
 *
 * <p>{@code BottomNavigationView} can only tint a pill behind the icon; the label sits outside
 * it. The design wants the active state to wrap the whole item - icon and word together - in a
 * softly rounded block, in a bar that is a rounded rectangle rather than a capsule and a little
 * smaller than Material's default. That is a layout the stock component cannot produce, so this
 * view builds the five items itself from the same menu resource and leaves navigation to the
 * host: it reports taps through {@link OnItemSelectedListener} and is told which destination is
 * current through {@link #setSelectedId(int)}.
 */
public class StoryTabBar extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(MenuItem item);
    }

    private final List<View> items = new ArrayList<>();
    private final List<MenuItem> menuItems = new ArrayList<>();
    @Nullable private OnItemSelectedListener listener;
    @IdRes private int selectedId = View.NO_ID;

    public StoryTabBar(Context context) {
        this(context, null);
    }

    public StoryTabBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(5);
        setPadding(pad, pad, pad, pad);
    }

    /** Builds one item per entry of the menu resource, in order. */
    public void setMenu(@MenuRes int menuRes) {
        removeAllViews();
        items.clear();
        menuItems.clear();
        // A PopupMenu is the least ceremony that yields an inflated Menu outside a toolbar.
        Menu menu = new PopupMenu(getContext(), this).getMenu();
        new android.view.MenuInflater(getContext()).inflate(menuRes, menu);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem menuItem = menu.getItem(i);
            View item = buildItem(menuItem);
            LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            addView(item, params);
            items.add(item);
            menuItems.add(menuItem);
        }
        applySelection();
    }

    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener listener) {
        this.listener = listener;
    }

    /** Marks the item whose id matches; any other id clears the selection. */
    public void setSelectedId(@IdRes int id) {
        selectedId = id;
        applySelection();
    }

    private View buildItem(MenuItem menuItem) {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(5), dp(4), dp(5));
        item.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_tab_item));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(menuItem.getTitle());
        item.setId(menuItem.getItemId());

        ImageView icon = new ImageView(context);
        Drawable drawable = menuItem.getIcon();
        if (drawable != null) {
            icon.setImageDrawable(drawable.mutate());
        }
        int size = dp(20);
        icon.setLayoutParams(new LayoutParams(size, size));
        icon.setImageTintList(ContextCompat.getColorStateList(context, R.color.bottom_nav_item_color));
        item.addView(icon);

        TextView label = new TextView(context);
        label.setText(menuItem.getTitle());
        label.setTextAppearance(R.style.TextAppearance_StoryStep_TabLabel);
        label.setTextColor(ContextCompat.getColorStateList(context, R.color.bottom_nav_item_color));
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(3);
        label.setLayoutParams(labelParams);
        label.setMaxLines(1);
        item.addView(label);

        item.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemSelected(menuItem);
            }
        });
        return item;
    }

    private void applySelection() {
        for (int i = 0; i < items.size(); i++) {
            boolean selected = menuItems.get(i).getItemId() == selectedId;
            View item = items.get(i);
            item.setSelected(selected);
            // State lists on the icon and label follow the item's selected state.
            for (int c = 0; c < ((LinearLayout) item).getChildCount(); c++) {
                ((LinearLayout) item).getChildAt(c).setSelected(selected);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
