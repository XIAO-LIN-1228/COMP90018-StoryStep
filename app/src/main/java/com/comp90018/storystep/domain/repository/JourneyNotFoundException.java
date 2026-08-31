package com.comp90018.storystep.domain.repository;

/** Raised when the selected journey identifier is not present in the active data source. */
public final class JourneyNotFoundException extends RuntimeException {
    public JourneyNotFoundException(String journeyId) {
        super("Journey not found: " + journeyId);
    }
}
