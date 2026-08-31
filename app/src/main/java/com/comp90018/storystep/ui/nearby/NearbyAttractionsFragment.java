package com.comp90018.storystep.ui.nearby;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationAccessPolicy;
import com.comp90018.storystep.platform.location.LocationSource;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Start destination: the list of nearby attractions.
 *
 * <p>The Fragment renders {@link NearbyUiState}, owns the Android permission and location
 * registration lifecycle, and forwards results to the ViewModel. It performs no geographic
 * calculation and applies no sample-quality rules of its own.
 *
 * <p>Nothing is requested or registered at launch. Location collection begins only when the user
 * presses the exploration control, and ends in {@code onStop}, so the app never collects a position
 * while it is not visible.
 */
public class NearbyAttractionsFragment extends Fragment
        implements AttractionAdapter.OnAttractionSelected, LocationSource.Listener {

    /** Bundle key read by {@link com.comp90018.storystep.ui.detail.AttractionDetailFragment}. */
    public static final String ARG_ATTRACTION_ID = "attractionId";

    private static final String PREFS_NAME = "storystep.location";

    /**
     * Android reports "not granted" both before the first request and after a permanent denial.
     * The app has to remember that it asked in order to tell those apart.
     */
    private static final String KEY_PERMISSION_REQUESTED = "location_permission_requested";

    /**
     * Whether the purpose explanation has already been shown in this screen's lifetime. Held in
     * instance state rather than preferences: it gates one extra tap, and a user returning in a
     * new session is better served by seeing the explanation again than by a dialog with no
     * context.
     */
    private static final String KEY_EXPLANATION_ACKNOWLEDGED = "explanation_acknowledged";

    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private NearbyAttractionsViewModel viewModel;
    private AttractionAdapter adapter;
    private LocationSource locationSource;
    private SharedPreferences preferences;

    private ActivityResultLauncher<String[]> permissionLauncher;

    /** True while this screen has an active location registration. */
    private boolean tracking;

    /** True once the user has seen the purpose explanation and can be asked for permission. */
    private boolean explanationAcknowledged;

    private RecyclerView list;
    private ProgressBar loadingIndicator;
    private TextView statusMessage;
    private TextView originLabel;
    private MaterialButton retryButton;
    private View homeMap;
    private MaterialCardView continueJourneyCard;
    private TextInputLayout searchContainer;
    private ChipGroup filterGroup;
    private TextView sectionTitle;
    private TextView seeAllButton;
    private boolean listMode;
    private MaterialButton sessionButton;
    private View permissionNotice;
    private TextView permissionNoticeMessage;
    private MaterialButton permissionNoticeAction;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (savedInstanceState != null) {
            explanationAcknowledged =
                    savedInstanceState.getBoolean(KEY_EXPLANATION_ACKNOWLEDGED, false);
        }
        // Registered here rather than in onViewCreated: the contract requires registration before
        // the Fragment reaches STARTED, and it must survive a configuration change.
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> onPermissionResult());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nearby_attractions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.attraction_list);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        statusMessage = view.findViewById(R.id.status_message);
        originLabel = view.findViewById(R.id.origin_label);
        retryButton = view.findViewById(R.id.retry_button);
        homeMap = view.findViewById(R.id.home_map);
        continueJourneyCard = view.findViewById(R.id.continue_journey_card);
        searchContainer = view.findViewById(R.id.search_container);
        filterGroup = view.findViewById(R.id.filter_group);
        sectionTitle = view.findViewById(R.id.nearby_title);
        seeAllButton = view.findViewById(R.id.see_all_button);
        sessionButton = view.findViewById(R.id.session_button);
        permissionNotice = view.findViewById(R.id.permission_notice);
        permissionNoticeMessage = view.findViewById(R.id.permission_notice_message);
        permissionNoticeAction = view.findViewById(R.id.permission_notice_action);

        adapter = new AttractionAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        StoryStepApplication application =
                (StoryStepApplication) requireActivity().getApplication();
        locationSource = application.getAppContainer().createLocationSource();

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(NearbyAttractionsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);

        seeAllButton.setOnClickListener(v -> setListMode(!listMode));
        view.findViewById(R.id.resume_journey_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_nearby_to_exploration));
        sessionButton.setOnClickListener(v -> onSessionButtonClicked());
        viewModel.loadIfNeeded();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Without this a rotation on the explanation would send the user back to the explanation
        // instead of on to the permission dialog.
        outState.putBoolean(KEY_EXPLANATION_ACKNOWLEDGED, explanationAcknowledged);
    }

    @Override
    public void onStop() {
        // Foreground-only: a screen the user cannot see must not hold a location registration.
        stopTracking();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        // Release the adapter so the detached view hierarchy is not retained.
        list.setAdapter(null);
        super.onDestroyView();
    }

    // --- exploration control -------------------------------------------------------------------

    private void onSessionButtonClicked() {
        if (tracking) {
            stopTracking();
            viewModel.onLocationTrackingStopped();
            return;
        }
        LocationAccess access = currentAccess();
        if (access.isGranted()) {
            startTracking(access);
            return;
        }
        if (!access.canRequest()) {
            // Permanently denied: a request would show nothing, so publish the state that offers
            // the settings route instead.
            viewModel.onLocationAccessChanged(access);
            return;
        }
        if (access == LocationAccess.NOT_REQUESTED && !explanationAcknowledged) {
            // Explain before the system dialog rather than after it. A request the user cannot
            // interpret is the one they deny, and a first denial is expensive: Android will not
            // show the dialog indefinitely. The notice's own action makes the actual request.
            explanationAcknowledged = true;
            viewModel.onLocationAccessChanged(access);
            return;
        }
        requestLocationPermissions();
    }

    private void requestLocationPermissions() {
        // Android reports "not granted" both before the first request and after a permanent
        // denial, so the app has to record that it asked.
        preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply();
        permissionLauncher.launch(LOCATION_PERMISSIONS);
    }

    private void onPermissionResult() {
        // The per-permission booleans are not consulted directly: a re-check goes through the same
        // policy as every other path, so granted, approximate-only and denied are classified once.
        LocationAccess access = currentAccess();
        if (access.isGranted()) {
            startTracking(access);
        } else {
            viewModel.onLocationAccessChanged(access);
        }
    }

    private void startTracking(LocationAccess access) {
        viewModel.onLocationAccessChanged(access);
        tracking = true;
        sessionButton.setText(R.string.nearby_stop_exploring);
        locationSource.startUpdates(this);
    }

    private void stopTracking() {
        if (!tracking) {
            return;
        }
        tracking = false;
        locationSource.stopUpdates();
        if (sessionButton != null) {
            sessionButton.setText(R.string.nearby_start_exploring);
        }
    }

    private LocationAccess currentAccess() {
        Context context = requireContext();
        boolean fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean rationale = ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean requested = preferences.getBoolean(KEY_PERMISSION_REQUESTED, false);
        return LocationAccessPolicy.evaluate(fine, coarse, rationale, requested);
    }

    /** Location services are off device-wide; the app's own settings page cannot turn them on. */
    private void openLocationSettings() {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    // --- LocationSource.Listener ---------------------------------------------------------------

    @Override
    public void onLocationChanged(LocationSample sample) {
        // elapsedRealtime is the same monotonic clock the sample's timestamp came from, so the two
        // are safe to subtract; wall-clock time is not used for any interval.
        viewModel.onLocationSample(sample, SystemClock.elapsedRealtime());
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        viewModel.onLocationUnavailable(reason);
    }

    // --- rendering -----------------------------------------------------------------------------

    private void render(NearbyUiState state) {
        NearbyUiState.Status status = state.getStatus();
        boolean hasItems = !state.getItems().isEmpty();
        boolean loading = status == NearbyUiState.Status.LOADING;

        // Content is shown whenever there is any, even under a blocking status: losing location
        // should reduce what the screen claims, not empty it.
        list.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        loadingIndicator.setVisibility(loading && !hasItems ? View.VISIBLE : View.GONE);
        originLabel.setText(originLabelText(state.getOriginLabel()));

        Integer message = statusMessageFor(status);
        boolean showMessage = message != null && !hasItems;
        statusMessage.setVisibility(showMessage ? View.VISIBLE : View.GONE);
        if (showMessage) {
            statusMessage.setText(message);
        }

        configureRecoveryAction(status);
        renderPermissionNotice(status);
        adapter.submitList(state.getItems());
    }

    @Nullable
    private Integer statusMessageFor(NearbyUiState.Status status) {
        switch (status) {
            case ERROR:
                return R.string.nearby_error;
            case EMPTY:
                return R.string.nearby_empty;
            case WAITING_FOR_FIX:
                return R.string.nearby_waiting_for_fix;
            default:
                return null;
        }
    }

    /**
     * Offers a retry only where retrying would do something. Location-access recovery is not
     * handled here: it lives in the notice, which is visible whether or not the list has content.
     */
    private void configureRecoveryAction(NearbyUiState.Status status) {
        if (status == NearbyUiState.Status.ERROR) {
            showRecovery(R.string.nearby_retry, v -> viewModel.load());
        } else {
            retryButton.setVisibility(View.GONE);
            retryButton.setOnClickListener(null);
        }
    }

    /**
     * Shows why location is wanted, or why it is currently unusable, together with the one action
     * that can resolve it.
     *
     * <p>Independent of whether the list has items. Reference-area content is on screen from
     * launch, so anything shown only on an empty list would never be seen at all — which is what
     * previously kept the purpose explanation from ever reaching the user.
     */
    private void renderPermissionNotice(NearbyUiState.Status status) {
        int message;
        int actionLabel;
        View.OnClickListener action;
        switch (status) {
            case PERMISSION_REQUIRED:
                message = R.string.nearby_permission_rationale;
                actionLabel = R.string.nearby_grant_access;
                action = v -> requestLocationPermissions();
                break;
            case PERMISSION_DENIED:
                message = R.string.nearby_permission_denied;
                actionLabel = R.string.nearby_grant_access;
                action = v -> requestLocationPermissions();
                break;
            case PERMISSION_PERMANENTLY_DENIED:
                message = R.string.nearby_permission_permanently_denied;
                actionLabel = R.string.nearby_open_settings;
                action = v -> openAppSettings();
                break;
            case LOCATION_UNAVAILABLE:
                message = R.string.nearby_location_unavailable;
                actionLabel = R.string.nearby_open_settings;
                action = v -> openLocationSettings();
                break;
            default:
                permissionNotice.setVisibility(View.GONE);
                permissionNoticeAction.setOnClickListener(null);
                return;
        }
        permissionNotice.setVisibility(View.VISIBLE);
        permissionNoticeMessage.setText(message);
        permissionNoticeAction.setText(actionLabel);
        permissionNoticeAction.setOnClickListener(action);
    }

    private void showRecovery(int labelRes, View.OnClickListener action) {
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setText(labelRes);
        retryButton.setOnClickListener(action);
    }

    private int originLabelText(NearbyUiState.OriginLabel label) {
        switch (label) {
            case LIVE_PRECISE:
                return R.string.nearby_origin_live_precise;
            case LIVE_APPROXIMATE:
                return R.string.nearby_origin_live_approximate;
            case REFERENCE_AREA:
            default:
                return R.string.nearby_origin_reference_area;
        }
    }

    @Override
    public void onAttractionSelected(AttractionListItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ATTRACTION_ID, item.getId());
        NavController navController = NavHostFragment.findNavController(this);
        navController.navigate(R.id.action_nearby_to_detail, arguments);
    }

    private void setListMode(boolean enabled) {
        listMode = enabled;
        homeMap.setVisibility(enabled ? View.GONE : View.VISIBLE);
        continueJourneyCard.setVisibility(enabled ? View.GONE : View.VISIBLE);
        searchContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        filterGroup.setVisibility(enabled ? View.VISIBLE : View.GONE);
        sectionTitle.setText(enabled ? R.string.nearby_title : R.string.home_nearby);
        seeAllButton.setText(enabled ? R.string.nav_explore : R.string.home_see_all);

        View fragmentView = getView();
        if (fragmentView == null) {
            return;
        }
        View sectionHeader = fragmentView.findViewById(R.id.nearby_section_header);
        ViewGroup.LayoutParams rawParams = sectionHeader.getLayoutParams();
        if (rawParams instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) rawParams;
            params.topToBottom = enabled ? R.id.filter_group : R.id.continue_journey_card;
            sectionHeader.setLayoutParams(params);
        }
    }
}
