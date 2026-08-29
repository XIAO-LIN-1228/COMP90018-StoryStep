package com.comp90018.storystep.domain.repository;

/**
 * Result channel for repository calls. Implementations decide their own threading; the
 * in-memory implementation answers synchronously, which keeps ViewModel tests deterministic.
 * Room- or network-backed implementations must post results back on the main thread.
 *
 * @param <T> the loaded payload type
 */
public interface RepositoryCallback<T> {

    void onSuccess(T result);

    void onError(Throwable error);
}
