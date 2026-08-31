package com.comp90018.storystep;

import android.app.Application;

import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.platform.lifecycle.AppVisibilityTracker;

/** Process entry point; owns the {@link AppContainer} used for manual dependency assembly. */
public class StoryStepApplication extends Application {

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
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
