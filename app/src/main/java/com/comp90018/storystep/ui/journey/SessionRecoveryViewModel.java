package com.comp90018.storystep.ui.journey;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.platform.location.LocationSource;

/**
 * Connects the recovery screen to the process-wide exploration session.
 *
 * <p>The screen never owns a location subscription. It observes and commands the same coordinator
 * as Nearby, so navigation cannot create a second session or make the two screens disagree about
 * whether collection is active.
 */
public final class SessionRecoveryViewModel extends ViewModel
        implements ExplorationCoordinator.Observer {

    private final ExplorationCoordinator coordinator;
    private final MutableLiveData<SessionSummary> session = new MutableLiveData<>();

    public SessionRecoveryViewModel(ExplorationCoordinator coordinator) {
        this.coordinator = coordinator;
        coordinator.addObserver(this);
    }

    public LiveData<SessionSummary> getSession() {
        return session;
    }

    public boolean onResumeRequested() {
        coordinator.requestResume();
        return coordinator.summary().getState() == SessionState.ACTIVE;
    }

    public void onFinishRequested() {
        coordinator.requestStop();
    }

    public void onDiscardRequested() {
        coordinator.requestDiscard();
    }

    @Override
    public void onSessionChanged(SessionSummary summary) {
        session.setValue(summary);
    }

    @Override
    public void onSample(LocationSample sample) {
        // Recovery renders checkpoint metadata, not live route points.
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        // Access recovery remains on Nearby, which owns the Android permission/settings flow.
    }

    @Override
    protected void onCleared() {
        coordinator.removeObserver(this);
        super.onCleared();
    }
}
