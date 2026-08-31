package com.comp90018.storystep.platform.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reports whether any of the app's activities is on screen.
 *
 * <p>A thin adapter: it translates Android lifecycle callbacks into one boolean and forwards it.
 * The session logic it feeds contains no Android types, which is what keeps that logic testable.
 *
 * <p>The count of started activities is what makes rotation safe. During a configuration change
 * the Activity is stopped and a new one started, which would otherwise look exactly like the user
 * leaving the app — and would pause the session on every rotation. {@code isChangingConfigurations}
 * distinguishes the two.
 *
 * <p>{@code ProcessLifecycleOwner} solves the same problem, but it delays its stop event by
 * roughly 700 ms to absorb the rotation gap. For foreground-only location that delay is a window in
 * which the app is invisible and still collecting, so the immediate count is preferred here. It
 * also avoids adding a dependency.
 */
public final class AppVisibilityTracker implements Application.ActivityLifecycleCallbacks {

    /** Receives the visibility of the app as a whole, not of any one screen. */
    public interface VisibilityListener {

        void onVisibilityChanged(boolean visible);
    }

    private final VisibilityListener listener;

    private int startedActivities;

    /**
     * The last value handed to the listener. Kept separately from the count so a rotation, which
     * momentarily drops the count to zero, reports nothing at all rather than a stop followed by a
     * start.
     */
    private boolean reportedVisible;

    public AppVisibilityTracker(VisibilityListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.listener = listener;
    }

    /** Begins observing. Call once, from {@code Application.onCreate}. */
    public void register(Application application) {
        application.registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities++;
        if (!reportedVisible) {
            reportedVisible = true;
            listener.onVisibilityChanged(true);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (isVisible() || activity.isChangingConfigurations()) {
            // Either another screen is still up, or a replacement Activity is about to start and
            // the app never actually left the screen.
            return;
        }
        reportedVisible = false;
        listener.onVisibilityChanged(false);
    }

    /** True while at least one Activity is started. */
    public boolean isVisible() {
        return startedActivities > 0;
    }

    // --- unused callbacks --------------------------------------------------------------------

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // Visibility is decided by started/stopped, not by creation.
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // Resumed is narrower than visible: a dialog or split screen would flap this.
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // See onActivityResumed.
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // Nothing to persist here; the session state lives in the coordinator.
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // Nothing to release; this tracker outlives every Activity.
    }
}
