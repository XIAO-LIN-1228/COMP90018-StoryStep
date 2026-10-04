package com.comp90018.storystep.ui;

import static org.junit.Assert.*;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.recommendation.RecommendationEngine.Mode;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsViewModel;
import com.google.android.material.chip.Chip;
import org.junit.Test;
import org.junit.runner.RunWith;

/** UI wiring and recreation checks; run on an emulator or disposable device profile. */
@RunWith(AndroidJUnit4.class)
public class RecommendationPresentationTest {
    @Test public void nearbyChoiceSurvivesActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = host(activity);
                host.getNavController().navigate(R.id.nearbyAttractionsFragment);
                host.getChildFragmentManager().executePendingTransactions();
                assertTrue(((Chip) activity.findViewById(R.id.order_recommended)).isChecked());
                activity.findViewById(R.id.order_nearby).performClick();
                assertEquals(Mode.NEARBY, model(host).getOrdering().getValue());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(((Chip) activity.findViewById(R.id.order_nearby)).isChecked());
                assertEquals(Mode.NEARBY, model(host(activity)).getOrdering().getValue());
            });
        }
    }

    @Test public void profileOpensSettingsWithCapabilityResultsAndNoMemoryEditorIsReachable() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = host(activity);
                host.getNavController().navigate(R.id.profileFragment);
                host.getChildFragmentManager().executePendingTransactions();
                assertNotNull(activity.findViewById(R.id.profile_saves_count));
                assertTrue(activity.findViewById(R.id.profile_settings).performClick());
                host.getChildFragmentManager().executePendingTransactions();
                assertEquals(R.id.settingsFragment, host.getNavController().getCurrentDestination().getId());
                TextView sensorStatus = activity.findViewById(R.id.profile_sensor_status);
                assertNotNull("Settings must display sensor capabilities", sensorStatus);
                String status = sensorStatus.getText().toString();
                assertTrue(status.contains("Accelerometer:"));
                assertTrue(status.contains("Magnetometer:"));
                assertEquals(0, activity.getResources().getIdentifier("memoryFragment", "id", activity.getPackageName()));
            });
        }
    }

    private static NavHostFragment host(MainActivity activity) {
        return (NavHostFragment) activity.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }

    private static NearbyAttractionsViewModel model(NavHostFragment host) {
        Fragment fragment = host.getChildFragmentManager().getPrimaryNavigationFragment();
        assertNotNull(fragment);
        return new ViewModelProvider(fragment, ViewModelFactory.from(fragment))
                .get(NearbyAttractionsViewModel.class);
    }
}
