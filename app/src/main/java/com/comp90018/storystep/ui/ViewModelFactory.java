package com.comp90018.storystep.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.ui.detail.AttractionDetailViewModel;
import com.comp90018.storystep.ui.exploration.ExplorationViewModel;
import com.comp90018.storystep.ui.journey.JourneysViewModel;
import com.comp90018.storystep.ui.journey.SessionRecoveryViewModel;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsViewModel;
import com.comp90018.storystep.ui.profile.PrivacyDataViewModel;
import com.comp90018.storystep.ui.saved.SavedViewModel;
import com.comp90018.storystep.ui.story.StoryViewModel;
import com.comp90018.storystep.ui.weather.WeatherViewModel;

/**
 * Builds ViewModels from the {@link AppContainer}, so Fragments never construct a repository,
 * database or network client themselves.
 */
public class ViewModelFactory implements ViewModelProvider.Factory {

    private final AppContainer appContainer;

    public ViewModelFactory(AppContainer appContainer) {
        this.appContainer = appContainer;
    }

    /** Convenience accessor for Fragments that only need the application's container. */
    public static ViewModelFactory from(Fragment fragment) {
        Application application = fragment.requireActivity().getApplication();
        return new ViewModelFactory(((StoryStepApplication) application).getAppContainer());
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(NearbyAttractionsViewModel.class)) {
            return (T) new NearbyAttractionsViewModel(
                    appContainer.getAttractionRepository(),
                    appContainer.getSavedAttractionRepository(),
                    appContainer.getReferenceOrigin(),
                    appContainer.getNearbySearchRadiusMeters(),
                    appContainer.getExplorationCoordinator());
        }
        if (modelClass.isAssignableFrom(AttractionDetailViewModel.class)) {
            return (T) new AttractionDetailViewModel(
                    appContainer.getAttractionRepository(),
                    appContainer.getSavedAttractionRepository(),
                    appContainer.getExplorationCoordinator());
        }
        if (modelClass.isAssignableFrom(ExplorationViewModel.class)) {
            return (T) new ExplorationViewModel(appContainer.getExplorationCoordinator());
        }
        if (modelClass.isAssignableFrom(JourneysViewModel.class)) {
            return (T) new JourneysViewModel(appContainer.getJourneyRepository());
        }
        if (modelClass.isAssignableFrom(StoryViewModel.class)) {
            return (T) new StoryViewModel(
                    appContainer.getAttractionRepository(),
                    appContainer.getSessionStore(),
                    appContainer.getExplorationCoordinator());
        }
        if (modelClass.isAssignableFrom(WeatherViewModel.class)) {
            return (T) new WeatherViewModel(
                    appContainer.getWeatherRepository(),
                    appContainer.getExplorationCoordinator(),
                    appContainer.getReferenceOrigin());
        }
        if (modelClass.isAssignableFrom(SavedViewModel.class)) {
            return (T) new SavedViewModel(
                    appContainer.getSavedAttractionRepository(),
                    appContainer.getReferenceOrigin());
        }
        if (modelClass.isAssignableFrom(PrivacyDataViewModel.class)) {
            return (T) new PrivacyDataViewModel(appContainer.getSavedAttractionRepository());
        }
        if (modelClass.isAssignableFrom(SessionRecoveryViewModel.class)) {
            return (T) new SessionRecoveryViewModel(
                    appContainer.getExplorationCoordinator());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
