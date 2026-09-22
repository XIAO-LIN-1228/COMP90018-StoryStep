package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.theme.ThemeProgress;

/** Local journey-owned content and progress, delivered on the main executor. */
public interface ThemeRepository {
    /** Null means this is a regular (non-themed) journey. */
    void load(String sessionId, RepositoryCallback<ThemeProgress> callback);
    void setSkipped(String sessionId, String stopId, boolean skipped, RepositoryCallback<ThemeProgress> callback);
    void answer(String sessionId, String stopId, int option, RepositoryCallback<ThemeProgress> callback);
}
