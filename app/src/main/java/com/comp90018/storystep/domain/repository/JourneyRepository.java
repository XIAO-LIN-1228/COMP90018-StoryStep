package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.Journey;

import java.util.List;

/** Backend-independent contract for session history and journey details. */
public interface JourneyRepository {

    void loadJourneys(RepositoryCallback<List<Journey>> callback);

    void loadJourney(String journeyId, RepositoryCallback<Journey> callback);
}
