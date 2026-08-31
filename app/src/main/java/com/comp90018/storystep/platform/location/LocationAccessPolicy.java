package com.comp90018.storystep.platform.location;

/**
 * Turns raw Android permission signals into a {@link LocationAccess} value.
 *
 * <p>Deliberately a pure function of its arguments, with no Android imports, so the permission
 * matrix — including the cases that are awkward to reproduce on a device, such as
 * permanently-denied — can be covered by JVM unit tests.
 */
public final class LocationAccessPolicy {

    private LocationAccessPolicy() {
        // Utility class.
    }

    /**
     * @param fineGranted      result of checking {@code ACCESS_FINE_LOCATION}
     * @param coarseGranted    result of checking {@code ACCESS_COARSE_LOCATION}
     * @param showRationale    {@code shouldShowRequestPermissionRationale} for the coarse
     *                         permission; meaningful only when nothing is granted
     * @param alreadyRequested whether this app has asked at least once before, which the app must
     *                         remember itself because the platform does not report it
     */
    public static LocationAccess evaluate(boolean fineGranted,
                                          boolean coarseGranted,
                                          boolean showRationale,
                                          boolean alreadyRequested) {
        // Fine implies coarse on every supported API level, so check it first.
        if (fineGranted) {
            return LocationAccess.PRECISE;
        }
        if (coarseGranted) {
            return LocationAccess.APPROXIMATE;
        }
        if (!alreadyRequested) {
            return LocationAccess.NOT_REQUESTED;
        }
        // After a first denial the system still offers a rationale. Once it stops offering one,
        // a further request would be a no-op, so only settings can recover.
        return showRationale ? LocationAccess.DENIED : LocationAccess.PERMANENTLY_DENIED;
    }
}