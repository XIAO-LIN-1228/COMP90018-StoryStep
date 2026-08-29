package com.comp90018.storystep.domain.repository;

/** Raised when a repository is asked for an attraction identifier it does not hold. */
public class AttractionNotFoundException extends RuntimeException {

    public AttractionNotFoundException(String attractionId) {
        super("Unknown attraction: " + attractionId);
    }
}
