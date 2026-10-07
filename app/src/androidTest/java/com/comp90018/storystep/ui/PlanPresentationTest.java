package com.comp90018.storystep.ui;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.data.repository.SampleAttractions;
import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.domain.plan.WalkPlan;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

/** Regressions for denied-start recovery and controls hidden below a collapsed sheet. */
@RunWith(AndroidJUnit4.class)
public class PlanPresentationTest {
    private final AppContainer app = ((StoryStepApplication) ApplicationProvider
            .getApplicationContext()).getAppContainer();
    private WalkPlan originalPlan;

    @Before public void keepExistingPlan() {
        onMain(() -> {
            assertFalse("Run these tests without an unfinished walk",
                    app.getExplorationCoordinator().summary().getState().hasSession());
            originalPlan = app.getWalkPlanner().plan();
            app.getWalkPlanner().clear();
        });
    }

    @After public void restorePlan() {
        if (originalPlan != null) {
            onMain(() -> app.getWalkPlanner().setPlan(originalPlan));
        }
    }

    @Test public void emptyPlannerShowsGuidanceAndAddActionWithoutDragging() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openPlanner(scenario);
            awaitSheet(scenario, BottomSheetBehavior.STATE_COLLAPSED);
            screenshot("empty-collapsed");
            scenario.onActivity(activity -> {
                assertFullyVisible(activity.findViewById(R.id.plan_empty_guidance));
                assertActionsClearOfNavigation(activity);
                assertFalse(activity.findViewById(R.id.plan_start).isEnabled());
                assertTrue(activity.findViewById(R.id.plan_add).performClick());
                assertEquals(R.id.nearbyAttractionsFragment,
                        host(activity).getNavController().getCurrentDestination().getId());
            });
        }
    }

    @Test public void populatedPlannerKeepsActionsVisibleAtEverySnapPositionAndAfterRotation() {
        onMain(() -> {
            for (com.comp90018.storystep.domain.model.Attraction place : SampleAttractions.get()) {
                app.getWalkPlanner().add(place);
            }
        });
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openPlanner(scenario);
            final int[] previousTop = {Integer.MAX_VALUE};
            for (int state : new int[] {BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED, BottomSheetBehavior.STATE_EXPANDED}) {
                scenario.onActivity(a -> sheet(a).setState(state));
                awaitSheet(scenario, state);
                scenario.onActivity(a -> {
                    assertActionsClearOfNavigation(a);
                    assertTrue(a.findViewById(R.id.plan_start).isEnabled());
                    int top = a.findViewById(R.id.plan_sheet).getTop();
                    assertTrue("Each expansion must move the sheet upward", top < previousTop[0]);
                    previousTop[0] = top;
                });
                screenshot("populated-" + state);
            }
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(PlanPresentationTest::assertActionsClearOfNavigation);
        }
    }

    @Test public void idleExplorationBackReturnsToPlannerWithoutAFinishDialog() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            openPlanner(scenario);
            scenario.onActivity(a -> {
                host(a).getNavController().navigate(R.id.action_plan_to_exploration);
                host(a).getChildFragmentManager().executePendingTransactions();
                assertTrue(a.findViewById(R.id.exploration_back).performClick());
                assertEquals(R.id.planFragment,
                        host(a).getNavController().getCurrentDestination().getId());
                assertNull(app.getExplorationCoordinator().summary().getId());
            });
        }
    }

    @Test public void deniedStartExplainsLocationAndKeepsThePlanWithoutCreatingAJourney() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("Run the denied-start case with location permission denied",
                PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION));
        SharedPreferences preferences = context.getSharedPreferences("storystep.location", Context.MODE_PRIVATE);
        boolean requested = preferences.getBoolean("location_permission_requested", false);
        try {
            for (boolean previouslyAsked : new boolean[] {false, true}) {
                preferences.edit().putBoolean("location_permission_requested", previouslyAsked).commit();
                onMain(() -> app.getWalkPlanner().add(SampleAttractions.get().get(0)));
                try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                    openPlanner(scenario);
                    scenario.onActivity(a -> {
                        assertTrue(a.findViewById(R.id.plan_start).performClick());
                        assertEquals(R.id.planFragment,
                                host(a).getNavController().getCurrentDestination().getId());
                        assertFalse(app.getExplorationCoordinator().summary().getState().hasSession());
                        assertNull(app.getExplorationCoordinator().summary().getId());
                        assertEquals(1, app.getWalkPlanner().plan().size());
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation().getRootInActiveWindow();
                    assertNotNull(root);
                    try {
                        assertTrue("Permission recovery must explain why location is needed",
                                containsText(root, context.getString(R.string.settings_location)));
                        assertTrue("Recovery must be cancellable",
                                containsText(root, context.getString(R.string.cancel)));
                        assertTrue("Offer either a permission request or system settings",
                                containsText(root, context.getString(R.string.nearby_grant_access))
                                || containsText(root, context.getString(R.string.nearby_open_settings)));
                    } finally { root.recycle(); }
                }
            }
        } finally {
            preferences.edit().putBoolean("location_permission_requested", requested).commit();
        }
    }

    private static boolean containsText(AccessibilityNodeInfo node, String text) {
        if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())) { return true; }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) { continue; }
            try { if (containsText(child, text)) { return true; } }
            finally { child.recycle(); }
        }
        return false;
    }

    private static void screenshot(String name) {
        Context context = ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File folder = new java.io.File(context.getExternalFilesDir(null), "plan-test-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        String filename = context.getResources().getConfiguration().fontScale + "-" + name + ".png";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(folder, filename))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        } finally { bitmap.recycle(); }
    }

    private static void openPlanner(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(a -> {
            host(a).getNavController().navigate(R.id.planFragment);
            host(a).getChildFragmentManager().executePendingTransactions();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void assertActionsClearOfNavigation(MainActivity activity) {
        Rect navigation = new Rect();
        assertTrue(activity.findViewById(R.id.main_bottom_navigation).getGlobalVisibleRect(navigation));
        for (int id : new int[] {R.id.plan_add, R.id.plan_start}) {
            View action = activity.findViewById(id);
            Rect bounds = assertFullyVisible(action);
            assertTrue("Planning action must be above bottom navigation", bounds.bottom <= navigation.top);
        }
    }

    private static Rect assertFullyVisible(View view) {
        assertNotNull(view);
        Rect visible = new Rect();
        assertTrue("View must be on screen: " + view.getId(), view.getGlobalVisibleRect(visible));
        assertEquals("View must not be clipped vertically", view.getHeight(), visible.height());
        assertEquals("View must not be clipped horizontally", view.getWidth(), visible.width());
        return visible;
    }

    private static void awaitSheet(ActivityScenario<MainActivity> scenario, int target) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicBoolean settled = new AtomicBoolean();
        do {
            scenario.onActivity(a -> settled.set(sheet(a).getState() == target
                    && a.findViewById(R.id.plan_add).getHeight() > 0));
            if (settled.get()) { return; }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Planner sheet did not reach state " + target);
    }

    private static BottomSheetBehavior<View> sheet(MainActivity a) {
        return BottomSheetBehavior.from(a.findViewById(R.id.plan_sheet));
    }

    private static NavHostFragment host(MainActivity a) {
        return (NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
}
