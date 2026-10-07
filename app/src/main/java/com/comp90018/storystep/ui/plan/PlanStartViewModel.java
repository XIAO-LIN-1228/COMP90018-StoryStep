package com.comp90018.storystep.ui.plan;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

/** Keeps a pending start across rotation; navigation requires a persisted session. */
public final class PlanStartViewModel extends ViewModel implements ExplorationCoordinator.Observer {
    public enum State { IDLE, STARTING, READY, FAILED }

    private final ExplorationCoordinator coordinator;
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);

    public PlanStartViewModel(ExplorationCoordinator coordinator) {
        this.coordinator = coordinator;
        coordinator.addObserver(this);
    }

    public LiveData<State> getState() {
        return state;
    }

    public void start(Attraction destination, LocationAccess access) {
        if (destination == null || state.getValue() == State.STARTING
                || state.getValue() == State.READY) {
            return;
        }
        if (coordinator.summary().getState().hasSession()) {
            state.setValue(State.READY);
            return;
        }
        coordinator.onLocationAccessChanged(access);
        if (!access.isGranted()) {
            state.setValue(State.FAILED);
            return;
        }
        state.setValue(State.STARTING);
        coordinator.requestStart(destination);
    }

    @Override
    public void onSessionChanged(SessionSummary summary) {
        if (state.getValue() != State.STARTING) {
            return;
        }
        if (summary.getState().hasSession()) {
            state.setValue(State.READY);
        } else if (summary.getState() != SessionState.STARTING) {
            state.setValue(State.FAILED);
        }
    }

    public void acknowledgeResult() {
        if (state.getValue() != State.STARTING) {
            state.setValue(State.IDLE);
        }
    }

    @Override public void onSample(LocationSample sample) {
        // A persisted session can open before its first location fix arrives.
    }

    @Override public void onLocationUnavailable(LocationSource.Reason reason) {
        // The coordinator represents an interrupted start as a paused session.
    }

    @Override
    protected void onCleared() {
        coordinator.removeObserver(this);
        super.onCleared();
    }
}
