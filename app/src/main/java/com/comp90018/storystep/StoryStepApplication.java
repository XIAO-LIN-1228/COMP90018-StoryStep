package com.comp90018.storystep;

import android.app.Application;

import com.comp90018.storystep.di.AppContainer;

/** Process entry point; owns the {@link AppContainer} used for manual dependency assembly. */
public class StoryStepApplication extends Application {

    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer(this);
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
