package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.model.Journey;
import com.comp90018.storystep.domain.repository.JourneyRepository;
import com.comp90018.storystep.domain.repository.JourneyNotFoundException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Deterministic UI prototype data; replace this binding in AppContainer when the API is ready. */
public final class InMemoryJourneyRepository implements JourneyRepository {

    private final List<Journey> journeys = Collections.unmodifiableList(Arrays.asList(
            new Journey("heritage-walk", "University Heritage Walk", "31 Aug 2026", 42, 2.3, 4),
            new Journey("museum-gardens", "Museum & Gardens", "24 Aug 2026", 65, 3.1, 5),
            new Journey("carlton-evening", "Carlton Evening Walk", "18 Aug 2026", 58, 2.0, 3)
    ));

    @Override
    public void loadJourneys(RepositoryCallback<List<Journey>> callback) {
        callback.onSuccess(journeys);
    }

    @Override
    public void loadJourney(String journeyId, RepositoryCallback<Journey> callback) {
        for (Journey journey : journeys) {
            if (journey.getId().equals(journeyId)) {
                callback.onSuccess(journey);
                return;
            }
        }
        callback.onError(new JourneyNotFoundException(journeyId));
    }
}
