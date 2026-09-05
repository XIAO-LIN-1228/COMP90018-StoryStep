package com.comp90018.storystep.ui.story;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;

/**
 * The story revealed for one attraction.
 *
 * <p>Content is read from the recorded visit first and from the attraction cache only as a
 * fallback. The visit holds a snapshot taken when the user was actually there, so a story opened
 * later still says what it said then, even if the place has since been re-fetched or dropped.
 *
 * <p>Showing the story marks the visit read. That is a presentation fact and never inserts a row:
 * reopening a story cannot create a second visit, and a story with no visit behind it — one
 * reached from browsing rather than from arriving — simply has nothing to mark.
 */
public class StoryViewModel extends ViewModel {

    private final AttractionRepository attractions;
    private final SessionStore sessionStore;
    private final ExplorationCoordinator coordinator;
    private final MutableLiveData<StoryUiState> uiState = new MutableLiveData<>();

    public StoryViewModel(AttractionRepository attractions,
                          SessionStore sessionStore,
                          ExplorationCoordinator coordinator) {
        this.attractions = attractions;
        this.sessionStore = sessionStore;
        this.coordinator = coordinator;
    }

    public LiveData<StoryUiState> getUiState() {
        return uiState;
    }

    /** Loads only if nothing has been published yet, so a rotation does not reload. */
    public void loadIfNeeded(String attractionId) {
        loadIfNeeded(attractionId, null);
    }

    /** Loads only if nothing has been published yet, so a rotation does not reload. */
    public void loadIfNeeded(String attractionId, String explicitSessionId) {
        if (uiState.getValue() == null) {
            load(attractionId, explicitSessionId);
        }
    }

    public void load(String attractionId) {
        load(attractionId, null);
    }

    /**
     * Loads the story for {@code attractionId}.
     *
     * @param explicitSessionId the walk the story belongs to, when it is being reopened from
     *                          history. Null means the running session, which is the right answer
     *                          only while the user is actually on that walk: a historical story
     *                          resolved against the running session would show a different walk's
     *                          record, or fall back to the cache and lose the text the visit
     *                          actually preserved.
     */
    public void load(String attractionId, String explicitSessionId) {
        if (attractionId == null) {
            uiState.setValue(StoryUiState.unavailable());
            return;
        }
        uiState.setValue(StoryUiState.loading());

        String sessionId = explicitSessionId != null
                ? explicitSessionId
                : coordinator.summary().getId();
        if (sessionId == null) {
            loadFromCache(attractionId);
            return;
        }
        sessionStore.findVisit(sessionId, attractionId, new RepositoryCallback<Visit>() {
            @Override
            public void onSuccess(Visit visit) {
                if (visit == null) {
                    // Opened by browsing rather than by arriving. There is content to show, but no
                    // visit behind it, and none is created by looking at it.
                    loadFromCache(attractionId);
                    return;
                }
                publish(visit);
                // Marked read whether or not it already was: the call is idempotent, and this is
                // the moment the story is actually in front of the user.
                markRead(sessionId, attractionId);
            }

            @Override
            public void onError(Throwable error) {
                loadFromCache(attractionId);
            }
        });
    }

    private void publish(Visit visit) {
        uiState.setValue(StoryUiState.content(
                visit.getAttractionId(), visit.getAttractionName(), visit.getStory()));
    }

    private void markRead(String sessionId, String attractionId) {
        sessionStore.markVisitRead(sessionId, attractionId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // The story is on screen and the visit knows it. Nothing left to tell the user.
            }

            @Override
            public void onError(Throwable error) {
                // Failing to mark it read costs the user a repeated prompt, not a lost visit, and
                // is not worth an error on a screen they opened to read something.
            }
        });
    }

    private void loadFromCache(String attractionId) {
        attractions.loadAttraction(attractionId, new RepositoryCallback<Attraction>() {
            @Override
            public void onSuccess(Attraction attraction) {
                uiState.setValue(StoryUiState.content(
                        attraction.getId(), attraction.getName(), attraction.getDescription()));
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(StoryUiState.unavailable());
            }
        });
    }
}
