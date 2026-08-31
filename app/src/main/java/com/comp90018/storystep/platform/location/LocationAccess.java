package com.comp90018.storystep.platform.location;

/**
 * The user's current level of location access, as far as the app is allowed to know.
 *
 * <p>Android does not tell an app whether a permission was never asked for or was permanently
 * denied; both report "not granted". The distinction here is inferred by
 * {@link LocationAccessPolicy} from the rationale flag plus whether the app has already asked, and
 * it matters because the two need different UI: one offers a request, the other must send the user
 * to system settings.
 */
public enum LocationAccess {

    /** Never requested. Show the purpose explanation and a Start action. */
    NOT_REQUESTED,

    /** {@code ACCESS_FINE_LOCATION} granted: full route precision and arrival detection. */
    PRECISE,

    /**
     * Only {@code ACCESS_COARSE_LOCATION} granted. Discovery is still useful, but route precision
     * and automatic arrival stay disabled and distances must be labelled approximate.
     */
    APPROXIMATE,

    /** Denied, but the system will still show a dialog if asked again. */
    DENIED,

    /** Denied such that a further request shows nothing; only system settings can recover. */
    PERMANENTLY_DENIED;

    /** True when some position data can be obtained. */
    public boolean isGranted() {
        return this == PRECISE || this == APPROXIMATE;
    }

    /** True when asking again would actually show the user a dialog. */
    public boolean canRequest() {
        return this == NOT_REQUESTED || this == DENIED;
    }
}
