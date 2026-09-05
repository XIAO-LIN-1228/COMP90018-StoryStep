package com.comp90018.storystep.platform.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * {@link ConnectivityMonitor} backed by {@code ConnectivityManager}.
 *
 * <p><b>This is a hint, not a gate.</b> A true answer means the device believes it has a validated
 * connection, which is not the same as the weather service being reachable — captive portals,
 * DNS failures and a provider outage all report online. So callers must still handle a request
 * failing after this said yes, and the repository treats it only as a way to skip a request that
 * is certain to fail and to explain an offline state to the user.
 *
 * <p>Requires {@code ACCESS_NETWORK_STATE}. Any failure to read the state — a missing permission
 * raises {@code SecurityException}, and some devices throw from the service itself — answers
 * {@code true}: an optimistic guess costs one failed request that is handled anyway, whereas a
 * pessimistic one would suppress a request that would have worked.
 */
public final class AndroidConnectivityMonitor implements ConnectivityMonitor {

    private final Context applicationContext;

    public AndroidConnectivityMonitor(Context context) {
        this.applicationContext = context.getApplicationContext();
    }

    @Override
    public boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager)
                applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return true;
        }
        try {
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return false;
            }
            // NET_CAPABILITY_VALIDATED is what distinguishes a connection that reached the
            // internet from one that merely associated with a network. Both report INTERNET.
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException unavailable) {
            return true;
        }
    }
}
