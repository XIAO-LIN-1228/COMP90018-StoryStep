package com.comp90018.storystep.ui;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.widget.StoryTabBar;

/**
 * The single Activity of the application. It hosts the navigation graph and applies system-bar
 * insets; every other screen is a Fragment. No calculation, sensor or location work belongs here.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * osmdroid's global configuration, set once before any map inflates.
     *
     * <p>The OpenStreetMap tile policy requires an identifying user agent, and the default
     * cache location is external storage, which this app has no permission for and no wish to
     * use. Tiles go to the app's own cache directory, where the system can reclaim them.
     */
    private void configureMapTiles() {
        org.osmdroid.config.IConfigurationProvider config =
                org.osmdroid.config.Configuration.getInstance();
        config.setUserAgentValue(getPackageName());
        java.io.File tileCache = new java.io.File(getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(tileCache);
        config.setOsmdroidTileCache(new java.io.File(tileCache, "tiles"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureMapTiles();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) {
            throw new IllegalStateException("Navigation host is missing");
        }
        NavController navController = navHost.getNavController();
        StoryTabBar bottomNavigation = findViewById(R.id.main_bottom_navigation);
        bottomNavigation.setMenu(R.menu.main_bottom_navigation);
        bottomNavigation.setBackdrop(findViewById(R.id.nav_host_fragment));
        // The bar reports taps; NavigationUI does the same destination handling it would for a
        // BottomNavigationView, so top-level back-stack behaviour is unchanged.
        bottomNavigation.setOnItemSelectedListener(item ->
                NavigationUI.onNavDestinationSelected(item, navController));
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destinationId = destination.getId();
            boolean topLevel = destinationId == R.id.nearbyAttractionsFragment
                    || destinationId == R.id.journeysFragment
                    || destinationId == R.id.profileFragment;
            bottomNavigation.setSelectedId(destinationId);
            bottomNavigation.setVisibility(topLevel ? android.view.View.VISIBLE
                    : android.view.View.GONE);
        });
    }
}
