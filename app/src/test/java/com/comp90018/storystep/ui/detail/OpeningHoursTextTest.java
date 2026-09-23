package com.comp90018.storystep.ui.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OpeningHoursTextTest {

    @Test
    public void spellsOutDaysAndPutsEachRuleOnItsOwnLine() {
        assertEquals("Mon–Fri 09:00–17:00\nSat, Sun 10:00–16:00",
                OpeningHoursText.format("Mo-Fr 09:00-17:00; Sa,Su 10:00-16:00"));
    }

    @Test
    public void alwaysOpenSaysSoInWords() {
        assertEquals("Open all hours", OpeningHoursText.format("24/7"));
    }

    @Test
    public void whatItCannotReadItShowsAsTagged() {
        // Conditionals and comments are beyond this formatter; the original is still readable.
        String odd = "Mo-Su 10:00-18:00 open \"ring the bell\"";
        assertEquals("Mon–Sun 10:00–18:00 open \"ring the bell\"",
                OpeningHoursText.format(odd));
    }

    @Test
    public void nothingRecordedIsNothingShown() {
        assertNull(OpeningHoursText.format(null));
        assertNull(OpeningHoursText.format("   "));
        assertNull(OpeningHoursText.format(";;"));
    }

    @Test
    public void feeSpeaksPlainlyAndKeepsAnythingItDoesNotKnow() {
        assertEquals("free", OpeningHoursText.feeLabel("no", "free", "charged"));
        assertEquals("charged", OpeningHoursText.feeLabel("yes", "free", "charged"));
        assertEquals("$5 for adults",
                OpeningHoursText.feeLabel("$5 for adults", "free", "charged"));
        assertNull(OpeningHoursText.feeLabel(null, "free", "charged"));
    }

    @Test
    public void accessIsOnlyClaimedForTheThreeValuesOsmDefines() {
        assertNull(OpeningHoursText.accessLabel(null));
        assertNull(OpeningHoursText.accessLabel("designated"));
        org.junit.Assert.assertNotNull(OpeningHoursText.accessLabel("yes"));
        org.junit.Assert.assertNotNull(OpeningHoursText.accessLabel("limited"));
        org.junit.Assert.assertNotNull(OpeningHoursText.accessLabel("no"));
    }
}
