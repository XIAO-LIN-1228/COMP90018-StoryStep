package com.comp90018.storystep.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

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
 *
 * <p>The bar is frosted glass. Android has no backdrop blur for an ordinary view, so the bar
 * makes its own: given the page it floats over ({@link #setBackdrop(View)}), it draws the strip
 * beneath itself into a small bitmap, softens that with two passes of a box blur, and paints it
 * under the translucent surface before the items. The capture is refreshed only on frames the
 * page itself redraws, so a still page costs nothing.
 */
public class StoryTabBar extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(MenuItem item);
    }

    private final List<View> items = new ArrayList<>();
    private final List<MenuItem> menuItems = new ArrayList<>();
    @Nullable private OnItemSelectedListener listener;
    @IdRes private int selectedId = View.NO_ID;

    /** One capture pixel per this many screen pixels; the blur radius is in capture pixels. */
    private static final int FROST_SCALE = 12;
    private static final int FROST_RADIUS = 2;
    @Nullable private View backdrop;
    @Nullable private Bitmap frost;
    @Nullable private int[] frostPixels;
    private final Paint frostPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path frostClip = new Path();
    private final RectF frostBounds = new RectF();
    private boolean frameIsOurs;
    private final ViewTreeObserver.OnPreDrawListener backdropWatcher = () -> {
        // The page is about to draw; take a fresh capture on the next frame. Our own
        // invalidate would otherwise trigger this again and keep the display awake forever.
        if (frameIsOurs) {
            frameIsOurs = false;
        } else if (getVisibility() == VISIBLE) {
            frameIsOurs = true;
            invalidate();
        }
        return true;
    };

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

    /** The view the bar floats over, whose foot shows through the glass. */
    public void setBackdrop(@Nullable View backdrop) {
        if (this.backdrop != null) {
            this.backdrop.getViewTreeObserver().removeOnPreDrawListener(backdropWatcher);
        }
        this.backdrop = backdrop;
        if (backdrop != null) {
            backdrop.getViewTreeObserver().addOnPreDrawListener(backdropWatcher);
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (backdrop != null) {
            backdrop.getViewTreeObserver().removeOnPreDrawListener(backdropWatcher);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        drawFrost(canvas);
        super.draw(canvas);
    }

    private void drawFrost(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (backdrop == null || w == 0 || h == 0 || backdrop.getWidth() == 0) {
            return;
        }
        int cw = Math.max(1, w / FROST_SCALE);
        int ch = Math.max(1, h / FROST_SCALE);
        if (frost == null || frost.getWidth() != cw || frost.getHeight() != ch) {
            frost = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
            frostPixels = new int[cw * ch];
        }
        // The page, drawn small and shifted so the strip under the bar lands on the bitmap.
        Canvas capture = new Canvas(frost);
        capture.drawColor(0xFFFFFFFF);
        capture.scale(1f / FROST_SCALE, 1f / FROST_SCALE);
        capture.translate(backdrop.getLeft() - getLeft(), backdrop.getTop() - getTop());
        backdrop.draw(capture);
        blur(frost, frostPixels, cw, ch);
        float radius = getResources().getDisplayMetrics().density * 20f;
        frostBounds.set(0, 0, w, h);
        frostClip.rewind();
        frostClip.addRoundRect(frostBounds, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(frostClip);
        canvas.drawBitmap(frost, null, frostBounds, frostPaint);
        canvas.restoreToCount(saved);
    }

    /** Two passes of a box blur, horizontal then vertical, in place. */
    private static void blur(Bitmap bitmap, int[] px, int w, int h) {
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 2; pass++) {
            boxBlur(px, tmp, w, h, true);
            boxBlur(tmp, px, w, h, false);
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void boxBlur(int[] in, int[] out, int w, int h, boolean horizontal) {
        int outer = horizontal ? h : w;
        int inner = horizontal ? w : h;
        for (int o = 0; o < outer; o++) {
            for (int i = 0; i < inner; i++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int k = -FROST_RADIUS; k <= FROST_RADIUS; k++) {
                    int j = i + k;
                    if (j < 0 || j >= inner) {
                        continue;
                    }
                    int c = horizontal ? in[o * w + j] : in[j * w + o];
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                    n++;
                }
                int index = horizontal ? o * w + i : i * w + o;
                out[index] = 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
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
        // No contentDescription: the label below carries the same words, and setting both makes a
        // screen reader say the tab's name twice. What the label cannot carry is that this is a
        // tab and whether it is the current one, which the node info below supplies.
        item.setId(menuItem.getItemId());
        ViewCompat.setAccessibilityDelegate(item, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                                                          AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setRoleDescription(getContext().getString(R.string.nav_tab_role));
                info.setSelected(host.isSelected());
                info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat
                        .obtain(0, 1, menuItems.indexOf(menuItem), 1, false, host.isSelected()));
            }
        });

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
