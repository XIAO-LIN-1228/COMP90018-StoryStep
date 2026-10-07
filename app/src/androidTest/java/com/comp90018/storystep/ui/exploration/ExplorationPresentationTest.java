package com.comp90018.storystep.ui.exploration;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.domain.guidance.DirectionGuidance;
import com.comp90018.storystep.domain.guidance.GuidanceSnapshot;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.plan.PlannedRoute;
import com.comp90018.storystep.domain.plan.WalkPlan;
import com.comp90018.storystep.ui.MainActivity;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.widget.PlaceMapView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synthetic presentation fixtures, not physical GPS evidence. No journeys are stored. */
@RunWith(AndroidJUnit4.class)
public class ExplorationPresentationTest {
    private static final GeoPoint HERE = new GeoPoint(-37.7963, 144.9614);
    private static final Attraction TARGET = Attraction.seeded("presentation-target", "Campus walk",
            AttractionCategory.PARK, "Synthetic display fixture.", IndoorClassification.OUTDOOR,
            new GeoPoint(-37.7943, 144.9614));

    @Test public void mapShowsPositionSeparatedTrailSegmentsAndLiveCompass() {
        try (ActivityScenario<MainActivity> scenario = open()) {
            show(scenario, true);
            settled();
            scenario.onActivity(a -> {
                assertMapAndStopVisible(a);
                MapView map = map(a);
                assertEquals(2L, map.getOverlays().stream().filter(o -> o instanceof Marker).count());
                assertEquals("Two separately outlined segments and one planned path",
                        5L, map.getOverlays().stream().filter(o -> o instanceof Polyline).count());
                assertEquals(1L, map.getOverlays().stream().filter(o -> o instanceof Polyline
                        && ((Polyline) o).getOutlinePaint().getPathEffect() != null).count());
                assertEquals(a.getString(R.string.exploration_route_legend),
                        ((TextView) a.findViewById(R.id.exploration_route_legend)).getText().toString());
                assertTrue(map.getZoomLevelDouble() >= 15.0);
                assertTrue(((TextView) a.findViewById(R.id.exploration_compass_heading))
                        .getText().toString().contains("Facing east"));
                assertEquals(-90f, a.findViewById(R.id.exploration_compass_needle).getRotation(), 0.1f);
                assertEquals(-90f, a.findViewById(R.id.exploration_direction_arrow).getRotation(), 0.1f);
                assertTrue(a.findViewById(R.id.exploration_recenter).isEnabled());
                map.getController().setCenter(new org.osmdroid.util.GeoPoint(-37.82, 144.99));
                a.findViewById(R.id.exploration_recenter).performClick();
            });
            settled();
            scenario.onActivity(a -> {
                assertEquals(HERE.getLatitude(), map(a).getMapCenter().getLatitude(), 0.0001);
                assertEquals(HERE.getLongitude(), map(a).getMapCenter().getLongitude(), 0.0001);
            });
            screenshot("position-trail-compass");
        }
    }

    @Test public void photoActionsRemainSeparateAndReachableInPortraitAndLandscape() {
        try (ActivityScenario<MainActivity> scenario = open()) {
            show(scenario, true);
            checkPhotoActions(scenario);
            screenshot("photos-portrait");
            scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            long deadline = SystemClock.uptimeMillis() + 8_000L;
            AtomicBoolean rotated = new AtomicBoolean();
            do {
                scenario.onActivity(a -> rotated.set(a.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE));
                if (rotated.get()) { break; }
                SystemClock.sleep(50L);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("The test device must allow rotation", rotated.get());
            show(scenario, true);
            checkPhotoActions(scenario);
            screenshot("photos-landscape");
            scenario.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }
    }

    @Test public void noFixKeepsTheMapVisibleWithoutInventingPositionOrCompass() {
        try (ActivityScenario<MainActivity> scenario = open()) {
            show(scenario, false);
            settled();
            scenario.onActivity(a -> {
                assertMapAndStopVisible(a);
                assertFalse(a.findViewById(R.id.exploration_recenter).isEnabled());
                assertEquals(View.INVISIBLE, a.findViewById(R.id.exploration_compass_needle).getVisibility());
                assertEquals(View.GONE, a.findViewById(R.id.exploration_direction_arrow).getVisibility());
                assertEquals(a.getString(R.string.exploration_map_waiting),
                        ((TextView) a.findViewById(R.id.exploration_location_status)).getText().toString());
                assertFalse(((TextView) a.findViewById(R.id.exploration_direction)).getText()
                        .toString().contains("no compass on this device"));
                assertEquals(a.getString(R.string.exploration_route_estimate_legend),
                        ((TextView) a.findViewById(R.id.exploration_route_legend)).getText().toString());
            });
            screenshot("waiting-for-gps");
        }
    }

    private static ActivityScenario<MainActivity> open() {
        StoryStepApplication app = ApplicationProvider.getApplicationContext();
        assertFalse("Run on an emulator without an unfinished walk",
                app.getAppContainer().getExplorationCoordinator().summary().getState().hasSession());
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(a -> {
            host(a).getNavController().navigate(R.id.explorationFragment);
            host(a).getChildFragmentManager().executePendingTransactions();
        });
        return scenario;
    }

    private static void show(ActivityScenario<MainActivity> scenario, boolean located) {
        scenario.onActivity(a -> {
            ExplorationFragment fragment = (ExplorationFragment) host(a).getChildFragmentManager()
                    .getPrimaryNavigationFragment();
            ExplorationViewModel model = new ViewModelProvider(fragment, ViewModelFactory.from(fragment))
                    .get(ExplorationViewModel.class);
            // Stop the real coordinator/timer observer before supplying a labeled UI fixture.
            model.onCleared();
            RouteSnapshot route = located ? route() : RouteSnapshot.empty();
            SessionSummary session = new SessionSummary("presentation-only", SessionState.ACTIVE,
                    null, 1_700_000_000_000L, 192_000L, 2);
            ((MutableLiveData<ExplorationUiState>) model.getUiState()).setValue(ExplorationUiState.from(
                    session, TARGET, route, null, false, false, MovementSnapshot.unavailable(),
                    located ? DirectionGuidance.compute(HERE, TARGET.getLocation(), 90.0)
                            : GuidanceSnapshot.unavailable(), null, false, 248L, 2.0));
            WalkPlan plan = WalkPlan.EMPTY.with(TARGET);
            PlannedRoute preview = located ? PlannedRoute.walked(Arrays.asList(HERE,
                    new GeoPoint(-37.7957, 144.9614), new GeoPoint(-37.7957, 144.9620),
                    new GeoPoint(-37.7943, 144.9620), TARGET.getLocation()), 340.0, 300_000L)
                    : PlannedRoute.straight(plan, null);
            ((MutableLiveData<ExplorationMapState>) model.getMapState()).setValue(
                    ExplorationMapState.from(TARGET.getLocation(), route, preview, plan.getStops()));
        });
    }

    private static RouteSnapshot route() {
        return new RouteSnapshot(148.0, Arrays.asList(
                new RoutePoint(new GeoPoint(-37.7974, 144.9609), 5f, 1000L, 1),
                new RoutePoint(new GeoPoint(-37.7970, 144.9612), 5f, 11000L, 1),
                new RoutePoint(new GeoPoint(-37.7968, 144.9614), 5f, 21000L, 2),
                new RoutePoint(HERE, 5f, 31000L, 2)), 2,
                new LocationSample(HERE, 5f, 31000L, 31000L, LocationOrigin.DEVICE));
    }

    private static void checkPhotoActions(ActivityScenario<MainActivity> scenario) {
        settled();
        scenario.onActivity(a -> {
            NestedScrollView scroll = a.findViewById(R.id.exploration_dashboard_scroll);
            View camera = a.findViewById(R.id.exploration_camera_button);
            Rect rectangle = new Rect(0, 0, camera.getWidth(), camera.getHeight());
            ((ViewGroup) scroll.getChildAt(0)).offsetDescendantRectToMyCoords(camera, rectangle);
            scroll.scrollTo(0, Math.max(0, rectangle.top - (scroll.getHeight() - rectangle.height()) / 2));
        });
        settled();
        scenario.onActivity(a -> {
            View camera = a.findViewById(R.id.exploration_camera_button);
            View library = a.findViewById(R.id.exploration_library_button);
            assertMapAndStopVisible(a);
            Rect cameraBounds = fullyVisible(camera);
            Rect libraryBounds = fullyVisible(library);
            assertTrue("The actions have separate touch areas", cameraBounds.right < libraryBounds.left);
            assertTrue(camera.hasOnClickListeners() && camera.isEnabled());
            assertTrue(library.hasOnClickListeners() && library.isEnabled());
            assertEquals(a.getString(R.string.exploration_take_photo), ((TextView) camera).getText().toString());
            assertEquals(a.getString(R.string.exploration_add_photo), ((TextView) library).getText().toString());
        });
    }

    private static void assertMapAndStopVisible(MainActivity a) {
        View map = a.findViewById(R.id.exploration_map);
        assertTrue("Information cards must not collapse the map", map.getHeight()
                >= 100 * a.getResources().getDisplayMetrics().density);
        fullyVisible(map);
        fullyVisible(a.findViewById(R.id.stop_exploration_button));
        Rect legend = fullyVisible(a.findViewById(R.id.exploration_route_legend));
        Rect recenter = fullyVisible(a.findViewById(R.id.exploration_recenter));
        assertTrue("The route legend must not sit underneath the recenter control",
                legend.right < recenter.left);
    }

    private static Rect fullyVisible(View view) {
        Rect visible = new Rect();
        assertTrue(view.getResources().getResourceEntryName(view.getId()), view.getGlobalVisibleRect(visible));
        assertEquals("No vertical clipping", view.getHeight(), visible.height());
        assertEquals("No horizontal clipping", view.getWidth(), visible.width());
        return visible;
    }

    private static MapView map(MainActivity a) {
        return (MapView) ((PlaceMapView) a.findViewById(R.id.exploration_map)).getChildAt(0);
    }

    private static NavHostFragment host(MainActivity a) {
        return (NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }

    private static void settled() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(500L);
    }

    private static void screenshot(String name) {
        Context context = ApplicationProvider.getApplicationContext();
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File folder = new java.io.File(context.getExternalFilesDir(null), "exploration-test-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        String filename = context.getResources().getConfiguration().fontScale + "-" + name + ".png";
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(folder, filename))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
