package com.comp90018.storystep.ui.nearby;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationAccessPolicy;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.WeatherText;
import com.comp90018.storystep.ui.weather.WeatherUiState;
import com.comp90018.storystep.ui.weather.WeatherViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Start destination: the list of nearby attractions.
 *
 * <p>The Fragment renders {@link NearbyUiState} and owns the Android permission flow, which needs
 * an Activity. It holds no session state of its own: the session and its location subscription
 * belong to the coordinator behind the ViewModel, which is why rotating the screen or navigating
 * away no longer disturbs a running session.
 *
 * <p>Nothing is requested at launch. Collection begins only when the user presses the exploration
 * control, and the coordinator ends it as soon as the app stops being visible.
 */
public class NearbyAttractionsFragment extends Fragment
        implements AttractionAdapter.OnAttractionSelected, AttractionAdapter.OnSaveToggled {

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
    private SharedPreferences preferences;

    private ActivityResultLauncher<String[]> permissionLauncher;

    /** True once the user has seen the purpose explanation and can be asked for permission. */
    private boolean explanationAcknowledged;

    private RecyclerView list;
    private ProgressBar loadingIndicator;
    private TextView statusMessage;
    private TextView originLabel;
    private MaterialButton retryButton;
    private View homeMap;
    private MaterialCardView continueJourneyCard;
    private View homeHeader;
    private View nearbyBackButton;
    private MaterialCardView weatherCard;
    private TextView weatherReading;
    private WeatherViewModel weatherViewModel;
    private TextView homeTitle;
    private TextInputLayout searchContainer;
    private ChipGroup filterGroup;
    private TextView sectionTitle;
    private TextView seeAllButton;
    private boolean listMode;
    private NearbyUiState.SessionPhase currentSessionPhase = NearbyUiState.SessionPhase.IDLE;
    private MaterialButton sessionButton;
    private MaterialButton stopButton;
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
        homeHeader = view.findViewById(R.id.home_header);
        nearbyBackButton = view.findViewById(R.id.nearby_back_button);
        weatherCard = view.findViewById(R.id.weather_card);
        weatherReading = view.findViewById(R.id.weather_reading);
        homeTitle = view.findViewById(R.id.home_title);
        searchContainer = view.findViewById(R.id.search_container);
        filterGroup = view.findViewById(R.id.filter_group);
        sectionTitle = view.findViewById(R.id.nearby_title);
        seeAllButton = view.findViewById(R.id.see_all_button);
        sessionButton = view.findViewById(R.id.session_button);
        stopButton = view.findViewById(R.id.stop_button);
        permissionNotice = view.findViewById(R.id.permission_notice);
        permissionNoticeMessage = view.findViewById(R.id.permission_notice_message);
        permissionNoticeAction = view.findViewById(R.id.permission_notice_action);

        adapter = new AttractionAdapter(this, this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(NearbyAttractionsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSaveFailed().observe(getViewLifecycleOwner(), this::reportSaveFailure);

        // Its own ViewModel rather than a field on the nearby one: weather has a different source,
        // a different failure mode and a different refresh rule, and a weather outage must not be
        // able to put the attraction list into an error state.
        weatherViewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(WeatherViewModel.class);
        weatherViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderWeather);
        // Tapping the card retries, which is the only recovery a user has for a failed fetch.
        weatherCard.setOnClickListener(v -> weatherViewModel.load());
        weatherViewModel.load();

        seeAllButton.setOnClickListener(v -> setListMode(!listMode));
        nearbyBackButton.setOnClickListener(v -> setListMode(false));
        view.findViewById(R.id.resume_journey_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_nearby_to_session_recovery));
        sessionButton.setOnClickListener(v -> onSessionButtonClicked());
        stopButton.setOnClickListener(v -> viewModel.onStopRequested());
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
    public void onStart() {
        super.onStart();
        // Access can have been changed in system settings while the app was away. Re-checking here
        // is what stops the screen from offering actions that would silently do nothing.
        viewModel.onLocationAccessChanged(currentAccess());
    }

    @Override
    public void onDestroyView() {
        // Release the adapter so the detached view hierarchy is not retained.
        list.setAdapter(null);
        super.onDestroyView();
    }

    // --- exploration control -------------------------------------------------------------------

    /**
     * The primary control. What it does depends on the session phase, which the ViewModel
     * publishes; this method never inspects a local tracking flag, because there is no longer one
     * to disagree with the session.
     */
    private void onSessionButtonClicked() {
        NearbyUiState state = viewModel.getUiState().getValue();
        NearbyUiState.SessionPhase phase = state == null
                ? NearbyUiState.SessionPhase.IDLE
                : state.getSessionPhase();
        if (phase.isBusy()) {
            // A start or stop is already in flight; a second press must not open or close another.
            return;
        }
        switch (phase) {
            case ACTIVE:
                viewModel.onStopRequested();
                return;
            case PAUSED_BACKGROUND:
            case PAUSED_ACCESS_LOST:
                resumeIfAccessAllows();
                return;
            default:
                startIfAccessAllows();
        }
    }

    private void startIfAccessAllows() {
        if (withAccess()) {
            viewModel.onStartRequested();
        }
    }

    private void resumeIfAccessAllows() {
        if (withAccess()) {
            viewModel.onResumeRequested();
        }
    }

    /**
     * Ensures location access before a session command, requesting it if that is still possible.
     *
     * @return true when the command may proceed now
     */
    private boolean withAccess() {
        LocationAccess access = currentAccess();
        if (access.isGranted()) {
            viewModel.onLocationAccessRequested(access);
            return true;
        }
        if (!access.canRequest()) {
            // Permanently denied: a request would show nothing, so publish the state that offers
            // the settings route instead.
            viewModel.onLocationAccessRequested(access);
            return false;
        }
        if (access == LocationAccess.NOT_REQUESTED && !explanationAcknowledged) {
            // Explain before the system dialog rather than after it. A request the user cannot
            // interpret is the one they deny, and a first denial is expensive: Android will not
            // show the dialog indefinitely. The notice's own action makes the actual request.
            explanationAcknowledged = true;
            viewModel.onExplanationRequired();
            return false;
        }
        requestLocationPermissions();
        return false;
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
        viewModel.onLocationAccessRequested(access);
        if (access.isGranted()) {
            // The user asked for a session by pressing the control; granting is the last thing
            // that was missing, so continue rather than making them press it again.
            onSessionButtonClicked();
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
        renderPermissionNotice(status, state.getSessionPhase());
        renderSessionControls(state.getSessionPhase());
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
    private void renderSessionControls(NearbyUiState.SessionPhase phase) {
        currentSessionPhase = phase;
        switch (phase) {
            case ACTIVE:
                sessionButton.setText(R.string.nearby_stop_exploring);
                break;
            case PAUSED_BACKGROUND:
            case PAUSED_ACCESS_LOST:
                sessionButton.setText(R.string.nearby_resume_exploring);
                break;
            case STARTING:
                sessionButton.setText(R.string.nearby_starting);
                break;
            case STOPPING:
                sessionButton.setText(R.string.nearby_stopping);
                break;
            case IDLE:
            default:
                sessionButton.setText(R.string.nearby_start_exploring);
                break;
        }
        // Inert rather than hidden while a transition is in flight: a control that vanishes under
        // the user's finger is worse than one that briefly does nothing.
        sessionButton.setEnabled(!phase.isBusy());
        // A paused session must be endable without resuming it first.
        stopButton.setVisibility(phase.isPaused() ? View.VISIBLE : View.GONE);
        updateContinueJourneyCard();
    }

    private void renderPermissionNotice(NearbyUiState.Status status,
                                        NearbyUiState.SessionPhase phase) {
        int message;
        int actionLabel;
        View.OnClickListener action;
        switch (status) {
            case PERMISSION_REQUIRED:
                message = R.string.nearby_permission_rationale;
                actionLabel = R.string.nearby_grant_access;
                action = v -> requestLocationPermissions();
                break;
            case WAITING_FOR_FIX:
                if (!phase.isPaused()) {
                    permissionNotice.setVisibility(View.GONE);
                    permissionNoticeAction.setOnClickListener(null);
                    return;
                }
                message = R.string.nearby_session_paused_background;
                actionLabel = R.string.nearby_resume_exploring;
                action = v -> resumeIfAccessAllows();
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
                if (phase == NearbyUiState.SessionPhase.PAUSED_BACKGROUND) {
                    // Explains why the totals stopped moving. Without it, returning to the app
                    // looks like the session silently died.
                    message = R.string.nearby_session_paused_background;
                    actionLabel = R.string.nearby_resume_exploring;
                    action = v -> resumeIfAccessAllows();
                    break;
                }
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
            case LIVE_PAUSED:
                return R.string.nearby_origin_live_paused;
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

    @Override
    public void onSaveToggled(AttractionListItem item) {
        viewModel.onSaveToggleRequested(item);
    }

    private void reportSaveFailure(Boolean failed) {
        if (!Boolean.TRUE.equals(failed)) {
            return;
        }
        Toast.makeText(requireContext(), R.string.detail_save_failed, Toast.LENGTH_SHORT).show();
        viewModel.consumeSaveFailure();
    }

    /**
     * Renders the weather card.
     *
     * <p>The card stays visible in every state, including unavailable: replacing it with nothing
     * would leave the user unable to retry, and a gap that appears and disappears is harder to
     * read than a line that says what it knows.
     */
    private void renderWeather(WeatherUiState state) {
        weatherReading.setText(
                WeatherText.format(getResources(), state, System.currentTimeMillis()));
        weatherCard.setClickable(state.isRetryable());
    }

    private void setListMode(boolean enabled) {
        listMode = enabled;
        homeHeader.setBackgroundResource(enabled
                ? android.R.color.transparent
                : R.drawable.bg_brand_header);
        nearbyBackButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        weatherCard.setVisibility(enabled ? View.GONE : View.VISIBLE);
        homeTitle.setText(enabled ? R.string.nearby_title : R.string.app_name);
        homeTitle.setTextColor(ContextCompat.getColor(requireContext(), enabled
                ? R.color.story_text_primary
                : R.color.white));
        homeMap.setVisibility(enabled ? View.GONE : View.VISIBLE);
        updateContinueJourneyCard();
        searchContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        filterGroup.setVisibility(enabled ? View.VISIBLE : View.GONE);
        sectionTitle.setText(enabled ? R.string.nearby_results : R.string.home_nearby);
        seeAllButton.setText(R.string.home_see_all);
        seeAllButton.setVisibility(enabled ? View.GONE : View.VISIBLE);

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

    /** The recovery card represents a real resumable session, never a permanent demo fixture. */
    private void updateContinueJourneyCard() {
        boolean resumable = currentSessionPhase == NearbyUiState.SessionPhase.PAUSED_BACKGROUND;
        continueJourneyCard.setVisibility(!listMode && resumable ? View.VISIBLE : View.GONE);
    }
}
