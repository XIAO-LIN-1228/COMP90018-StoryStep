package com.comp90018.storystep.ui;

import android.graphics.Rect;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;

import com.comp90018.storystep.R;

/**
 * Widens the tappable area of a control that is drawn smaller than the 48dp minimum.
 *
 * <p>Several of the app's icon buttons are drawn at 40dp because that is what the design asks
 * for — a back chevron floating over a photograph reads as a small control. Growing the view
 * itself would change the design; growing only the area that answers a touch does not, and it is
 * the area, not the drawing, that NFR-03.3 is about.
 *
 * <p>One delegate per parent is all the platform supports, so a row of several small controls
 * cannot be handled this way; those are sized up directly instead.
 */
public final class TouchTargets {

    private TouchTargets() {
        // Utility class.
    }

    /**
     * Expands {@code view}'s touch area to at least the minimum target size, centred on the view.
     *
     * <p>Deferred to the parent's next layout pass, because the view has no bounds to expand
     * until it has been measured.
     */
    public static void expand(final View view) {
        final View parent = (View) view.getParent();
        if (parent == null) {
            return;
        }
        final int minimum = view.getResources()
                .getDimensionPixelSize(R.dimen.minimum_touch_target);
        parent.post(() -> {
            Rect bounds = new Rect();
            view.getHitRect(bounds);
            int growHorizontally = Math.max(0, minimum - bounds.width()) / 2;
            int growVertically = Math.max(0, minimum - bounds.height()) / 2;
            if (growHorizontally == 0 && growVertically == 0) {
                return;
            }
            bounds.inset(-growHorizontally, -growVertically);
            parent.setTouchDelegate(new TouchDelegate(bounds, view));
        });
    }

    /** Expands every named child of {@code parent}; each must be a direct child. */
    public static void expandAll(ViewGroup parent, int... viewIds) {
        for (int viewId : viewIds) {
            View child = parent.findViewById(viewId);
            if (child != null) {
                expand(child);
            }
        }
    }
}
