package com.comp90018.storystep;

import android.app.Application;
import android.util.Log;

import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.platform.lifecycle.AppVisibilityTracker;

/** Process entry point; owns the {@link AppContainer} used for manual dependency assembly. */
public class StoryStepApplication extends Application {

    private static final String TAG = "StoryStepApplication";

    private AppContainer appContainer;
    private AppVisibilityTracker visibilityTracker;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer(this);
        // Registered at the process level rather than in an Activity or Fragment: the session must
        // stop collecting the moment the app leaves the screen, whichever screen was showing.
        visibilityTracker = new AppVisibilityTracker(
                visible -> appContainer.getExplorationCoordinator().onVisibilityChanged(visible));
        visibilityTracker.register(this);
        // Reads back a session the previous process left unfinished. It comes back paused and
        // registers nothing; only the user can decide to continue it.
        appContainer.getExplorationCoordinator().restoreInterruptedSession();
        sweepExpiredCaches();
    }

    /**
     * Removes provider copies past the D-06 retention window, once per process start.
     *
     * <p>Off the main thread, and its failure is swallowed: a cleanup that could not run is not a
     * reason to fail a launch, and the rows it would have removed are removed on the next start.
     * It touches no user-authored record, so nothing the user would miss depends on it.
     */
    private void sweepExpiredCaches() {
        appContainer.getIoExecutor().execute(() -> {
            try {
                appContainer.getCacheRetentionSweeper().sweep();
            } catch (RuntimeException error) {
                Log.w(TAG, "Cache retention sweep did not complete", error);
            }
        });
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
