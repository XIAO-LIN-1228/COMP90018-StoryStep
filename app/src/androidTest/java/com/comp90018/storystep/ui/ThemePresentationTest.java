package com.comp90018.storystep.ui;

import static org.junit.Assert.*;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.data.repository.ThemeCatalog;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.theme.*;
import com.comp90018.storystep.ui.theme.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses durable fixtures, never labels injected positions as a real field acceptance run. */
@RunWith(AndroidJUnit4.class)
public class ThemePresentationTest {
    private final AppContainer app = ((StoryStepApplication) ApplicationProvider.getApplicationContext()).getAppContainer();
    @Test public void previewAndRotationDoNotStartExploration() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            navigate(scenario); awaitReady(scenario);
            scenario.onActivity(a -> {
                assertTrue(model(a).getState().getValue().canStart());
                assertEquals(3, ((LinearLayout) a.findViewById(R.id.theme_stops)).getChildCount());
                assertEquals(View.GONE, row(a, 0).findViewById(R.id.theme_stop_select).getVisibility());
                assertEquals(a.getString(R.string.theme_story_locked), ((TextView) row(a, 0).findViewById(R.id.theme_stop_story)).getText().toString());
            });
            scenario.recreate(); awaitReady(scenario);
            scenario.onActivity(a -> assertTrue(model(a).getState().getValue().canStart()));
            screenshot("theme-preview.png");
        }
    }
    @Test public void restoredThemeAllowsSkipUndoQuizAndRecreationThenShowsReadOnlyResults() throws Exception {
        Result<SessionSummary> created = new Result<>(); app.getSessionStore().createForTheme(ThemeCatalog.parkville(), created);
        String id = created.await().getId();
        try {
            ThemeStop stop = ThemeCatalog.parkville().stops.get(0); Result<Visit> visit = new Result<>();
            app.getSessionStore().recordVisit(new Visit(id, stop.id, 1000, stop.name, stop.story, true), visit); visit.await();
            onMain(() -> app.getExplorationCoordinator().restoreInterruptedSession());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                navigate(scenario); awaitReady(scenario);
                scenario.onActivity(a -> {
                    assertEquals(1, model(a).getState().getValue().progress.arrivedCount());
                    assertEquals(stop.story, ((TextView) row(a, 0).findViewById(R.id.theme_stop_story)).getText().toString());
                    assertEquals(View.GONE, row(a, 1).findViewById(R.id.theme_stop_options).getVisibility());
                    ((RadioGroup) row(a, 0).findViewById(R.id.theme_stop_options)).getChildAt(0).performClick();
                });
                awaitReady(scenario);
                scenario.onActivity(a -> {
                    assertEquals(Integer.valueOf(0), model(a).getState().getValue().progress.answer(stop.id));
                    row(a, 1).findViewById(R.id.theme_stop_skip).performClick();
                });
                awaitReady(scenario);
                scenario.recreate(); awaitReady(scenario);
                screenshot("theme-restored-progress.png");
                scenario.onActivity(a -> {
                    assertEquals(1, model(a).getState().getValue().progress.skippedCount());
                    assertEquals(Integer.valueOf(0), model(a).getState().getValue().progress.answer(stop.id));
                    row(a, 1).findViewById(R.id.theme_stop_skip).performClick();
                });
                awaitReady(scenario);
                scenario.onActivity(a -> { assertEquals(0, model(a).getState().getValue().progress.skippedCount()); model(a).finish(); });
                awaitFinished(scenario);
                scenario.onActivity(a -> {
                    assertFalse(model(a).getState().getValue().editable());
                    assertEquals(View.GONE, a.findViewById(R.id.theme_finish).getVisibility());
                    assertEquals(View.VISIBLE, a.findViewById(R.id.theme_results).getVisibility());
                    assertFalse(((RadioGroup) row(a, 0).findViewById(R.id.theme_stop_options)).getChildAt(0).isEnabled());
                });
            }
        } finally {
            onMain(() -> app.getExplorationCoordinator().requestDiscard());
            Result<Boolean> deletion = new Result<>(); app.getHistoryRepository().delete(id, deletion); deletion.await();
        }
    }
    private static void navigate(ActivityScenario<MainActivity> scenario) { scenario.onActivity(a -> {
        NavHostFragment host = host(a); host.getNavController().navigate(R.id.themeFragment);
        host.getChildFragmentManager().executePendingTransactions();
    }); }
    private static View row(MainActivity a, int i) { return ((LinearLayout) a.findViewById(R.id.theme_stops)).getChildAt(i); }
    private static NavHostFragment host(MainActivity a) { return (NavHostFragment) a.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment); }
    private static ThemeViewModel model(MainActivity a) { Fragment f = host(a).getChildFragmentManager().getPrimaryNavigationFragment();
        return new ViewModelProvider(f, ViewModelFactory.from(f)).get(ThemeViewModel.class); }
    private static void awaitReady(ActivityScenario<MainActivity> scenario) { await(scenario, false); }
    private static void awaitFinished(ActivityScenario<MainActivity> scenario) { await(scenario, true); }
    private static void await(ActivityScenario<MainActivity> scenario, boolean finished) {
        long deadline = android.os.SystemClock.uptimeMillis() + 15000; AtomicBoolean ready = new AtomicBoolean();
        do {
            scenario.onActivity(a -> { ThemeViewModel.State s = model(a).getState().getValue();
                ready.set(!s.loading && !s.busy && (!finished || s.progress != null && s.progress.finished)); });
            if (ready.get()) { return; } android.os.SystemClock.sleep(25);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Theme UI did not settle before deadline");
    }
    private static void onMain(Runnable work) { InstrumentationRegistry.getInstrumentation().runOnMainSync(work); }
    private static void screenshot(String name) {
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        java.io.File folder = new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "theme-test-evidence");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(folder, name))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
    private static final class Result<T> implements RepositoryCallback<T> {
        final CountDownLatch latch = new CountDownLatch(1); T value; Throwable error;
        public void onSuccess(T value) { this.value = value; latch.countDown(); }
        public void onError(Throwable error) { this.error = error; latch.countDown(); }
        T await() throws Exception { assertTrue("Database callback timed out", latch.await(15, TimeUnit.SECONDS)); assertNull(error); return value; }
    }
}
