package com.comp90018.storystep.platform.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The permission matrix.
 *
 * <p>Covered here rather than on a device because the interesting states — a first denial versus a
 * permanent one, and approximate-only access — are slow and unreliable to reproduce by hand, and
 * getting them wrong produces either a prompt loop or a dead end.
 */
public class LocationAccessPolicyTest {

    @Test
    public void fineAccessIsPrecise() {
        assertEquals(LocationAccess.PRECISE,
                LocationAccessPolicy.evaluate(true, true, false, true));
    }

    @Test
    public void fineAccessWinsEvenIfCoarseIsNotReportedGranted() {
        assertEquals(LocationAccess.PRECISE,
                LocationAccessPolicy.evaluate(true, false, false, true));
    }

    @Test
    public void coarseOnlyAccessIsApproximate() {
        assertEquals(LocationAccess.APPROXIMATE,
                LocationAccessPolicy.evaluate(false, true, false, true));
    }

    @Test
    public void nothingGrantedAndNeverAskedIsNotRequested() {
        // The rationale flag is false before the first request too, so it must not be read here.
        assertEquals(LocationAccess.NOT_REQUESTED,
                LocationAccessPolicy.evaluate(false, false, false, false));
    }

    @Test
    public void aFirstDenialIsRecoverable() {
        assertEquals(LocationAccess.DENIED,
                LocationAccessPolicy.evaluate(false, false, true, true));
    }

    @Test
    public void denialWithNoRationaleAfterAskingIsPermanent() {
        assertEquals(LocationAccess.PERMANENTLY_DENIED,
                LocationAccessPolicy.evaluate(false, false, false, true));
    }

    @Test
    public void onlyGrantedStatesReportAccess() {
        assertTrue(LocationAccess.PRECISE.isGranted());
        assertTrue(LocationAccess.APPROXIMATE.isGranted());
        assertFalse(LocationAccess.NOT_REQUESTED.isGranted());
        assertFalse(LocationAccess.DENIED.isGranted());
        assertFalse(LocationAccess.PERMANENTLY_DENIED.isGranted());
    }

    @Test
    public void aPermanentDenialMustNotBeRequestedAgain() {
        // Requesting again would show no dialog, so the UI has to offer settings instead.
        assertFalse(LocationAccess.PERMANENTLY_DENIED.canRequest());
        assertTrue(LocationAccess.NOT_REQUESTED.canRequest());
        assertTrue(LocationAccess.DENIED.canRequest());
    }
}