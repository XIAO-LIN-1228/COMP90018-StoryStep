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
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * The single Activity of the application. It hosts the navigation graph and applies system-bar
 * insets; every other screen is a Fragment. No calculation, sensor or location work belongs here.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        BottomNavigationView bottomNavigation = findViewById(R.id.main_bottom_navigation);
        // The root already reserves the system navigation-bar inset. BottomNavigationView also
        // applies that inset by default, which would squeeze its icons and labels into the top of
        // a fixed-height bar. Consume no additional inset here so the 68 dp component keeps the
        // proportions defined by the design system.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (v, insets) -> {
            v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), 0);
            return insets;
        });
        NavigationUI.setupWithNavController(bottomNavigation, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destinationId = destination.getId();
            boolean topLevel = destinationId == R.id.nearbyAttractionsFragment
                    || destinationId == R.id.journeysFragment
                    || destinationId == R.id.savedFragment
                    || destinationId == R.id.profileFragment;
            bottomNavigation.setVisibility(topLevel ? android.view.View.VISIBLE
                    : android.view.View.GONE);
        });
    }
}
