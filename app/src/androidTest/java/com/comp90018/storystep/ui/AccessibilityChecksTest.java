package com.comp90018.storystep.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.accessibility.AccessibilityChecks;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comp90018.storystep.R;

import androidx.navigation.fragment.NavHostFragment;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the platform accessibility checks over each main screen (NFR-03.3).
 *
 * <p>The checks are attached to Espresso's view actions, so each case performs one real action
 * against the screen's root: that is what triggers the scan of the whole hierarchy for missing
 * labels, touch targets below 48dp and insufficient contrast.
 *
 * <p>These are device checks. They confirm what a machine can see — a label exists, a target is
 * large enough. They do not establish reading order, whether a label is a useful sentence, or
 * behaviour at a large font scale, all of which still need the manual TalkBack walkthrough that
 * AT-Q03A and AT-Q03B describe.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityChecksTest {

    @BeforeClass
    public static void enableChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true);
    }

    @Test
    public void theThemeScreenPassesTheAutomatedChecks() {
        assertScreenIsClean(R.id.themeFragment);
    }

    @Test
    public void theNearbyScreenPassesTheAutomatedChecks() {
        assertScreenIsClean(R.id.nearbyAttractionsFragment);
    }

    @Test
    public void theJourneysScreenPassesTheAutomatedChecks() {
        assertScreenIsClean(R.id.journeysFragment);
    }

    @Test
    public void theSavedScreenPassesTheAutomatedChecks() {
        assertScreenIsClean(R.id.savedFragment);
    }

    @Test
    public void theProfileScreenPassesTheAutomatedChecks() {
        assertScreenIsClean(R.id.profileFragment);
    }

    @Test
    public void thePrivacyScreenPassesTheAutomatedChecks() {
        assertScreenIsClean(R.id.privacyDataFragment);
    }

    /**
     * Opens {@code destinationId} and performs one action, which is what makes the enabled checks
     * scan the hierarchy. A failure is reported by the check itself, not by an assertion here.
     */
    private void assertScreenIsClean(int destinationId) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);
                host.getNavController().navigate(destinationId);
                host.getChildFragmentManager().executePendingTransactions();
            });
            onView(isRoot()).perform(click());
        }
    }
}
