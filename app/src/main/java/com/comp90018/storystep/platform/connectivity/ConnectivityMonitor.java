package com.comp90018.storystep.platform.connectivity;

/**
 * Reports whether the device currently has usable network access, so repositories can choose
 * cached data and the UI can explain an offline state instead of showing a generic failure.
 */
public interface ConnectivityMonitor {

    boolean isOnline();
}
