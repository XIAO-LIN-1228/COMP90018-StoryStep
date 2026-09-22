package com.comp90018.storystep.ui.theme;

import androidx.lifecycle.*;
import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.data.repository.ThemeCatalog;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.theme.*;

/** Coordinates theme commands; location and arrival remain owned by ExplorationCoordinator. */
public final class ThemeViewModel extends ViewModel implements ExplorationCoordinator.Observer {
    public static final class State {
        public final ThemeProgress progress;
        public final SessionSummary session;
        public final boolean loading, busy, failed, historical, explorationFailed;
        public final String destinationId;
        State(ThemeProgress progress, SessionSummary session, boolean loading, boolean busy,
                boolean failed, boolean historical, String destinationId, boolean explorationFailed) {
            this.progress = progress; this.session = session; this.loading = loading;
            this.busy = busy; this.failed = failed; this.historical = historical; this.destinationId = destinationId;
            this.explorationFailed = explorationFailed;
        }
        public boolean editable() {
            return !historical && progress != null && !progress.finished && !busy && !loading
                    && progress.sessionId.equals(session.getId())
                    && (session.getState() == SessionState.ACTIVE || session.getState() == SessionState.PAUSED);
        }
        public boolean canStart() {
            return !historical && !busy && !loading
                    && (session.getState() == SessionState.IDLE || session.getState() == SessionState.COMPLETED);
        }
    }
    private final ThemeRepository repository;
    private final ExplorationCoordinator coordinator;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private String explicitSession;
    private ThemeProgress progress;
    private boolean loading, busy, failed, starting, cleared;
    private int readGeneration, operationGeneration;

    public ThemeViewModel(ThemeRepository repository, ExplorationCoordinator coordinator) {
        this.repository = repository; this.coordinator = coordinator;
        coordinator.addObserver(this);
        refresh();
    }
    public LiveData<State> getState() { return state; }
    public void showSession(String sessionId) {
        if (!java.util.Objects.equals(explicitSession, sessionId)) {
            explicitSession = sessionId; progress = null;
        }
        refresh();
    }
    public void refresh() {
        final int version = ++readGeneration;
        String id = explicitSession != null ? explicitSession : coordinator.summary().getId();
        if (progress != null && !progress.sessionId.equals(id)) { progress = null; }
        if (id == null) { progress = null; loading = false; publish(); return; }
        loading = true; publish();
        repository.load(id, new RepositoryCallback<ThemeProgress>() {
            @Override public void onSuccess(ThemeProgress value) {
                if (cleared || version != readGeneration) { return; }
                progress = value; loading = false; failed = false; publish();
            }
            @Override public void onError(Throwable error) {
                if (cleared || version != readGeneration) { return; }
                loading = false; failed = true; publish();
            }
        });
    }
    public void start() {
        if (!state.getValue().canStart()) { return; }
        starting = true; failed = false;
        coordinator.requestStartTheme(ThemeCatalog.parkville());
    }
    public void select(String stopId) {
        if (!state.getValue().editable() || progress.status(stopId) != ThemeProgress.Status.PENDING) { return; }
        coordinator.selectDestination(progress.theme.stop(stopId).attraction());
    }
    public void skip(String stopId, boolean skipped) {
        if (!state.getValue().editable()) { return; }
        String id = progress.sessionId;
        repository.setSkipped(id, stopId, skipped, commandCallback(() -> {
            Attraction destination = coordinator.getDestination();
            if (skipped && id.equals(coordinator.summary().getId()) && destination != null
                    && destination.getId().equals(stopId)) { coordinator.selectDestination(null); }
        }));
    }
    public void answer(String stopId, int option) {
        if (!state.getValue().editable()) { return; }
        repository.answer(progress.sessionId, stopId, option, commandCallback(() -> { }));
    }
    private RepositoryCallback<ThemeProgress> commandCallback(Runnable after) {
        final int operation = ++operationGeneration;
        ++readGeneration;
        busy = true; failed = false; publish();
        return new RepositoryCallback<ThemeProgress>() {
            @Override public void onSuccess(ThemeProgress ignored) {
                if (cleared || operation != operationGeneration) { return; }
                busy = false; after.run(); refresh();
            }
            @Override public void onError(Throwable error) {
                if (cleared || operation != operationGeneration) { return; }
                busy = false; loading = false; failed = true; publish();
            }
        };
    }
    public void pauseOrResume() {
        if (!state.getValue().editable()) { return; }
        if (coordinator.summary().getState() == SessionState.ACTIVE) { coordinator.requestPause(); }
        else { coordinator.requestResume(); }
    }
    public void finish() { if (state.getValue().editable()) { coordinator.requestStop(); } }
    @Override public void onSessionChanged(SessionSummary summary) {
        if (starting && summary.getState() != SessionState.STARTING) {
            starting = false;
            failed = summary.getState() == SessionState.IDLE;
        }
        refresh();
    }
    @Override public void onPendingStoryChanged(Visit visit) { if (visit != null) { refresh(); } }
    @Override public void onSample(LocationSample sample) { }
    @Override public void onLocationUnavailable(com.comp90018.storystep.platform.location.LocationSource.Reason reason) { }
    @Override public void onDurabilityChanged(boolean checkpointFailed) { publish(); }
    @Override public void onVisitSaveFailed(boolean failed) { publish(); }
    @Override public void onDestinationChanged(Attraction attraction) { publish(); }
    private void publish() {
        if (cleared) { return; }
        Attraction destination = coordinator.getDestination();
        boolean explorationFailed = explicitSession == null && (coordinator.hasCheckpointFailed()
                || coordinator.hasStopSaveFailed() || coordinator.hasVisitSaveFailed());
        state.setValue(new State(progress, coordinator.summary(), loading,
                busy || starting || coordinator.summary().getState() == SessionState.STARTING
                        || coordinator.summary().getState() == SessionState.STOPPING,
                failed || explorationFailed, explicitSession != null,
                destination == null ? null : destination.getId(), explorationFailed));
    }
    @Override protected void onCleared() {
        cleared = true; ++readGeneration; ++operationGeneration;
        coordinator.removeObserver(this);
        super.onCleared();
    }
}
