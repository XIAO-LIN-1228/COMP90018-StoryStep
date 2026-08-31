package com.comp90018.storystep.domain.model;

/**
 * Where a {@link LocationSample} came from.
 *
 * <p>Test and demonstration positions are kept visibly distinct from device fixes so a fixture can
 * never be presented to the user, or recorded in a session, as a real observation.
 */
public enum LocationOrigin {

    /** A fix reported by an Android location provider. */
    DEVICE,

    /**
     * A coordinate supplied by a test or a debug fixture. Never produced by the shipping location
     * adapter.
     */
    INJECTED
}