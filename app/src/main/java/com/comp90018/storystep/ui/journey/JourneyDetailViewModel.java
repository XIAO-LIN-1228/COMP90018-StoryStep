package com.comp90018.storystep.ui.journey;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionNotFoundException;

/**
 * Reads one recorded journey.
 *
 * <p>Everything it publishes came off the device: the visits carry their own snapshotted names and
 * story text, and the weather and movement were copied onto the session while it ran. Opening a
 * journey therefore works with no network and with the attraction cache empty.
 */
public class JourneyDetailViewModel extends ViewModel {

    private final HistoryRepository history;
    private final MutableLiveData<JourneyDetailUiState> uiState = new MutableLiveData<>();

    private String loadedSessionId;

    public JourneyDetailViewModel(HistoryRepository history) {
        this.history = history;
    }

    public LiveData<JourneyDetailUiState> getUiState() {
        return uiState;
    }

    /** Photographs being chosen for the publish card; survives rotation with the model. */
    private final MutableLiveData<java.util.List<android.net.Uri>> chosenPhotos =
            new MutableLiveData<>(new java.util.ArrayList<>());
    private final MutableLiveData<Boolean> publishing = new MutableLiveData<>(false);

    public LiveData<java.util.List<android.net.Uri>> getChosenPhotos() {
        return chosenPhotos;
    }

    public LiveData<Boolean> isPublishing() {
        return publishing;
    }

    public void addPhotos(java.util.List<android.net.Uri> photos) {
        java.util.List<android.net.Uri> all = new java.util.ArrayList<>(chosenPhotos.getValue());
        for (android.net.Uri photo : photos) {
            if (!all.contains(photo)) {
                all.add(photo);
            }
        }
        chosenPhotos.setValue(all);
    }

    public void removePhoto(android.net.Uri photo) {
        java.util.List<android.net.Uri> all = new java.util.ArrayList<>(chosenPhotos.getValue());
        all.remove(photo);
        chosenPhotos.setValue(all);
    }

    /**
     * Publishes the loaded walk: copies the chosen photographs into the app's own storage,
     * names the place the walk started, writes the record, then re-reads the detail so the
     * screen shows what was stored rather than what was typed. The heavy work runs on the
     * given executor; the copy is what takes the time.
     */
    public void publish(android.content.Context appContext,
                        java.util.concurrent.Executor io,
                        java.util.concurrent.Executor main,
                        String sessionId,
                        String title) {
        if (sessionId == null || title == null || title.trim().isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(publishing.getValue())) {
            return;
        }
        publishing.setValue(true);
        java.util.List<android.net.Uri> photos = new java.util.ArrayList<>(chosenPhotos.getValue());
        JourneyDetailUiState state = uiState.getValue();
        com.comp90018.storystep.domain.model.GeoPoint start = null;
        if (state != null && state.getStatus() == JourneyDetailUiState.Status.CONTENT
                && state.getDetail().hasRoute()) {
            start = state.getDetail().getSegments().get(0).getPoints().get(0).getPosition();
        }
        com.comp90018.storystep.domain.model.GeoPoint origin = start;
        io.execute(() -> {
            // Photographs taken on the walk are already in the session's directory; the ones
            // chosen here are copied in beside them, and the order is the order they arrived.
            java.util.List<String> files = com.comp90018.storystep.platform.media.JourneyPhotoStore
                    .names(appContext, sessionId);
            for (String added : com.comp90018.storystep.platform.media.JourneyPhotoStore
                    .importAll(appContext, sessionId, photos)) {
                if (!files.contains(added)) {
                    files.add(added);
                }
            }
            String place = com.comp90018.storystep.platform.location.PlaceNamer
                    .name(appContext, origin);
            main.execute(() -> history.publish(sessionId, title.trim(), place, files,
                    new RepositoryCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean written) {
                            publishing.setValue(false);
                            chosenPhotos.setValue(new java.util.ArrayList<>());
                            load(sessionId);
                        }

                        @Override
                        public void onError(Throwable error) {
                            publishing.setValue(false);
                        }
                    }));
        });
    }

    /** Loads only if this session has not been published yet, so a rotation does not re-read. */
    public void loadIfNeeded(String sessionId) {
        if (uiState.getValue() != null && sessionId != null
                && sessionId.equals(loadedSessionId)) {
            return;
        }
        load(sessionId);
    }

    public void load(String sessionId) {
        if (sessionId == null) {
            // Navigated here without an id. Nothing to look up, and the same dead end for the
            // user as a journey that no longer exists.
            uiState.setValue(JourneyDetailUiState.notFound());
            return;
        }
        loadedSessionId = sessionId;
        uiState.setValue(JourneyDetailUiState.loading());
        history.loadDetail(sessionId, new RepositoryCallback<SessionHistoryDetail>() {
            @Override
            public void onSuccess(SessionHistoryDetail detail) {
                uiState.setValue(JourneyDetailUiState.content(detail));
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(error instanceof SessionNotFoundException
                        ? JourneyDetailUiState.notFound()
                        : JourneyDetailUiState.error());
            }
        });
    }
}
