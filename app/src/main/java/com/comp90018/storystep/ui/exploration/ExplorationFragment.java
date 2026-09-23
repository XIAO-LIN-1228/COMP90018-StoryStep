package com.comp90018.storystep.ui.exploration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.guidance.CardinalDirection;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.platform.location.LocationSource;
import com.comp90018.storystep.ui.CardinalAnnouncer;
import com.comp90018.storystep.ui.DirectionLabels;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.WeatherText;
import com.comp90018.storystep.ui.weather.WeatherUiState;
import com.comp90018.storystep.ui.weather.WeatherViewModel;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.comp90018.storystep.ui.widget.PlaceMapView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.TimeUnit;

/**
 * The active exploration dashboard, bound to the process-wide session.
 *
 * <p>Every optional reading is rendered as explicitly unavailable when it is missing. A dashboard
 * that showed "0 m remaining" because no fix had arrived would be claiming the user had arrived.
 */
public class ExplorationFragment extends Fragment {

    private PlaceMapView map;
    private TextView offlinePill;
    private com.comp90018.storystep.platform.connectivity.ConnectivityMonitor connectivity;
    private boolean remainingUnavailable;

    /** Keeps the compass from narrating every tremor; see {@link CardinalAnnouncer}. */
    private final CardinalAnnouncer directionAnnouncer = new CardinalAnnouncer();

    private ExplorationViewModel viewModel;
    private com.comp90018.storystep.ui.theme.ThemeViewModel themeModel;

    /**
     * True once this screen has actually seen a session. Without it, opening the dashboard with no
     * session would immediately navigate away, and a second state update after the session ended
     * would navigate twice.
     */
    private boolean sessionSeen;

    private boolean navigatedAway;

    private TextView destinationNameView;
    private TextView durationView;
    private TextView stateChip;
    private TextView remainingView;
    private TextView travelledView;
    private TextView accuracyView;
    private TextView directionView;
    private TextView movementView;
    private TextView weatherView;
    private WeatherViewModel weatherViewModel;
    private final android.os.Handler weatherHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            weatherViewModel.refreshAge();
            weatherHandler.postDelayed(this, 30_000L);
        }
    };

    @Override public void onStart() {
        super.onStart();
        weatherHandler.post(weatherTick);
    }

    @Override public void onStop() {
        weatherHandler.removeCallbacks(weatherTick);
        super.onStop();
    }
    private ImageView directionArrow;
    private MaterialButton resumeButton;
    private MaterialButton retrySaveButton;
    private TextView noticeView;
    private MaterialButton stopButton;
    private View arrivalBanner;
    private TextView arrivalBannerText;
    private MaterialButton arrivalViewStoryButton;
    private MaterialButton arrivalLaterButton;
    private TextView visitSaveFailedNotice;
    private MaterialButton retryVisitButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exploration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        themeModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(com.comp90018.storystep.ui.theme.ThemeViewModel.class);
        TextView themeButton = view.findViewById(R.id.exploration_theme);
        themeButton.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.themeFragment));
        themeModel.getState().observe(getViewLifecycleOwner(), state -> {
            themeButton.setVisibility(state.progress != null || state.failed ? View.VISIBLE : View.GONE);
            themeButton.setText(state.progress == null ? getString(R.string.theme_view_progress)
                    : getString(R.string.theme_journey_summary, state.progress.theme.title,
                            state.progress.arrivedCount(), state.progress.theme.stops.size(), state.progress.skippedCount()));
        });
        map = view.findViewById(R.id.exploration_map);
        offlinePill = view.findViewById(R.id.exploration_offline);
        connectivity = new com.comp90018.storystep.platform.connectivity.AndroidConnectivityMonitor(
                requireContext().getApplicationContext());
        map.centerOn(((com.comp90018.storystep.StoryStepApplication) requireActivity()
                .getApplication()).getAppContainer().getReferenceOrigin());

        destinationNameView = view.findViewById(R.id.exploration_destination_name);
        durationView = view.findViewById(R.id.exploration_duration);
        stateChip = view.findViewById(R.id.exploration_state_chip);
        remainingView = view.findViewById(R.id.exploration_remaining);
        travelledView = view.findViewById(R.id.exploration_travelled);
        accuracyView = view.findViewById(R.id.exploration_accuracy);
        view.findViewById(R.id.exploration_camera_button).setOnClickListener(v -> takePhotoOfHere());
        view.findViewById(R.id.exploration_library_button).setOnClickListener(v ->
                pickPhotos.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        directionView = view.findViewById(R.id.exploration_direction);
        movementView = view.findViewById(R.id.exploration_movement);
        weatherView = view.findViewById(R.id.exploration_weather);
        directionArrow = view.findViewById(R.id.exploration_direction_arrow);
        resumeButton = view.findViewById(R.id.resume_exploration_button);
        retrySaveButton = view.findViewById(R.id.retry_save_button);
        stopButton = view.findViewById(R.id.stop_exploration_button);
        noticeView = view.findViewById(R.id.exploration_notice);

        view.findViewById(R.id.exploration_back).setOnClickListener(v -> showStopDialog());
        stopButton.setOnClickListener(v -> showStopDialog());
        resumeButton.setOnClickListener(v -> viewModel.onResumeRequested());
        retrySaveButton.setOnClickListener(v -> viewModel.onRetrySaveRequested());

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(ExplorationViewModel.class);

        arrivalBanner = view.findViewById(R.id.arrival_banner);
        arrivalBannerText = view.findViewById(R.id.arrival_banner_text);
        arrivalViewStoryButton = view.findViewById(R.id.arrival_view_story_button);
        arrivalLaterButton = view.findViewById(R.id.arrival_later_button);
        visitSaveFailedNotice = view.findViewById(R.id.visit_save_failed_notice);
        retryVisitButton = view.findViewById(R.id.retry_visit_button);
        arrivalLaterButton.setOnClickListener(v -> viewModel.onArrivalDismissed());
        retryVisitButton.setOnClickListener(v -> viewModel.onRetryVisitRequested());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);

        viewModel.getMapState().observe(getViewLifecycleOwner(), this::renderMap);

        // The same ViewModel the nearby screen uses. Both instances read one process-scoped
        // repository, so arriving here from the list does not issue a second request for an
        // observation that was fetched a moment ago.
        weatherViewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(WeatherViewModel.class);
        weatherViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderWeather);
        weatherView.setOnClickListener(v -> weatherViewModel.load());
        weatherViewModel.load();
    }

    /**
     * Renders the optional weather section.
     *
     * <p>An unavailable observation says so rather than showing a zero, and never blocks the rest
     * of the dashboard: a session has to be startable, watchable and stoppable with no weather at
     * all.
     */
    private void renderWeather(WeatherUiState state) {
        weatherView.setText(WeatherText.format(getResources(), state, System.currentTimeMillis()));
        weatherView.setClickable(state.isRetryable());
    }

    /** The file the camera is writing into, kept across the launch. */
    @Nullable private java.io.File pendingCapture;

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takePhoto =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
                    saved -> {
                        java.io.File file = pendingCapture;
                        pendingCapture = null;
                        if (file == null) {
                            return;
                        }
                        if (!Boolean.TRUE.equals(saved) || file.length() == 0) {
                            // Cancelled, or the camera wrote nothing: leave no empty file behind.
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
                        }
                        showPhotoCount();
                    });

    private final androidx.activity.result.ActivityResultLauncher<
            androidx.activity.result.PickVisualMediaRequest> pickPhotos =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts
                            .PickMultipleVisualMedia(8),
                    uris -> {
                        String sessionId = walkId();
                        if (uris == null || uris.isEmpty() || sessionId == null) {
                            return;
                        }
                        android.content.Context app = requireContext().getApplicationContext();
                        com.comp90018.storystep.di.AppContainer container = container();
                        container.getIoExecutor().execute(() -> {
                            com.comp90018.storystep.platform.media.JourneyPhotoStore
                                    .importAll(app, sessionId, uris);
                            container.getMainExecutor().execute(this::showPhotoCount);
                        });
                    });

    private com.comp90018.storystep.di.AppContainer container() {
        return ((com.comp90018.storystep.StoryStepApplication)
                requireActivity().getApplication()).getAppContainer();
    }

    /** The running walk's id, or null when there is no walk to attach a photograph to. */
    @Nullable
    private String walkId() {
        com.comp90018.storystep.domain.model.SessionSummary summary =
                container().getExplorationCoordinator().summary();
        return summary == null || summary.getId() == null || summary.getId().isEmpty()
                ? null : summary.getId();
    }

    private void takePhotoOfHere() {
        String sessionId = walkId();
        if (sessionId == null) {
            return;
        }
        java.io.File file = com.comp90018.storystep.platform.media.JourneyPhotoStore
                .newCapture(requireContext(), sessionId);
        android.net.Uri target = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().getPackageName() + ".fileprovider", file);
        pendingCapture = file;
        try {
            takePhoto.launch(target);
        } catch (android.content.ActivityNotFoundException noCamera) {
            pendingCapture = null;
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            com.google.android.material.snackbar.Snackbar.make(requireView(),
                    R.string.exploration_camera_unavailable,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
        }
    }

    /** Says how many photographs the walk has, and nothing at all when it has none. */
    private void showPhotoCount() {
        View view = getView();
        String sessionId = walkId();
        if (view == null) {
            return;
        }
        TextView count = view.findViewById(R.id.exploration_photo_count);
        int taken = sessionId == null ? 0
                : com.comp90018.storystep.platform.media.JourneyPhotoStore
                        .names(requireContext(), sessionId).size();
        count.setVisibility(taken == 0 ? View.GONE : View.VISIBLE);
        if (taken > 0) {
            count.setText(getString(taken == 1
                    ? R.string.exploration_photo_count
                    : R.string.exploration_photo_count_plural, taken));
        }
    }

    private void render(ExplorationUiState state) {
        destinationNameView.setText(state.getDestinationName() == null
                ? getString(R.string.exploration_no_destination)
                : state.getDestinationName());
        // Which stop of the plan, when a plan is being walked.
        TextView progress = requireView().findViewById(R.id.exploration_plan_progress);
        int[] at = ((com.comp90018.storystep.StoryStepApplication) requireActivity().getApplication())
                .getAppContainer().getExplorationCoordinator().planProgress();
        progress.setVisibility(at == null ? View.GONE : View.VISIBLE);
        if (at != null) {
            progress.setText(getString(R.string.plan_stop_of, at[0], at[1]));
        }
        durationView.setText(formatDuration(state.getActiveDurationMillis()));
        stateChip.setText(stateLabel(state));

        // The big figure is a figure. With no fix there is no figure, so it shows a dash and
        // the explanation moves to the label above it, where a sentence has room.
        remainingView.setText(state.hasRemainingDistance()
                ? getString(R.string.exploration_remaining_format,
                        DistanceText.format(getResources(), state.getRemainingDistanceMeters()))
                : getString(R.string.exploration_remaining_none));
        remainingUnavailable = !state.hasRemainingDistance();
        // Tiles come from the network; say so when there is none. A hint, not a gate: the walk
        // itself needs no connection, and cached tiles still draw.
        offlinePill.setVisibility(connectivity.isOnline() ? View.GONE : View.VISIBLE);
        travelledView.setText(getString(R.string.exploration_travelled_format,
                DistanceText.format(getResources(), state.getTravelledDistanceMeters())));
        // Photographs belong to a walk, so the row is there only while one is running.
        boolean walking = state.getState() != null && state.getState().hasSession();
        requireView().findViewById(R.id.exploration_photo_row)
                .setVisibility(walking ? View.VISIBLE : View.GONE);
        showPhotoCount();
        // Steps and climb: each column appears only when its sensor reported, and the row
        // only when at least one did.
        View fitness = requireView().findViewById(R.id.exploration_fitness);
        View stepsColumn = requireView().findViewById(R.id.exploration_steps_column);
        View climbColumn = requireView().findViewById(R.id.exploration_climb_column);
        Long steps = state.getSteps();
        Double climb = state.getAscentMeters();
        stepsColumn.setVisibility(steps == null ? View.GONE : View.VISIBLE);
        climbColumn.setVisibility(climb == null ? View.GONE : View.VISIBLE);
        fitness.setVisibility(steps == null && climb == null ? View.GONE : View.VISIBLE);
        if (steps != null) {
            ((TextView) requireView().findViewById(R.id.exploration_steps)).setText(
                    java.text.NumberFormat.getIntegerInstance().format(steps));
        }
        if (climb != null) {
            ((TextView) requireView().findViewById(R.id.exploration_climb)).setText(
                    DistanceText.format(getResources(), climb));
        }
        accuracyView.setText(state.hasAccuracy()
                ? getString(R.string.exploration_accuracy_format,
                        Math.round(state.getAccuracyMeters()))
                : getString(R.string.exploration_accuracy_unavailable));
        movementView.setText(DirectionLabels.movementOf(state.getMovementState()));
        renderDirection(state);

        // The durability warning replaces the standing notice rather than joining it: a second
        // line of small text under an unread one is a line nobody reads.
        noticeView.setText(state.isUnsavedProgressWarningVisible()
                ? getString(R.string.exploration_unsaved_progress)
                : getString(R.string.exploration_notice));

        resumeButton.setVisibility(state.isResumeVisible() ? View.VISIBLE : View.GONE);
        retrySaveButton.setVisibility(state.isRetrySaveVisible() ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(state.isStopEnabled());
        renderArrival(state);

        if (state.getState().hasSession()) {
            sessionSeen = true;
            return;
        }
        if (!sessionSeen || navigatedAway) {
            return;
        }
        navigatedAway = true;
        if (state.isFinished()) {
            // Straight to the finished walk. The prototype summary page that used to sit
            // between here and the real detail screen is gone.
            NavHostFragment.findNavController(this).navigate(
                    R.id.action_exploration_to_journey_detail,
                    com.comp90018.storystep.ui.journey.JourneysFragment
                            .finishedSessionArguments(this));
        } else {
            // Discarded: there is nothing left to show, and no journey to open.
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    /**
     * Shows the arrival prompt, or the fact that an arrival could not be saved.
     *
     * <p>The click listener is rebound on every render rather than once, because the attraction it
     * opens changes with the state. Reading the story does not mark it read here — the story
     * screen does that when it actually shows it, so a navigation that never lands loses nothing.
     */
    private void renderArrival(ExplorationUiState state) {
        boolean visible = state.isArrivalBannerVisible();
        arrivalBanner.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            String name = state.getArrivedAttractionName();
            arrivalBannerText.setText(getString(R.string.exploration_arrival_banner_format,
                    name == null ? getString(R.string.exploration_no_destination) : name));
            String attractionId = state.getArrivedAttractionId();
            arrivalViewStoryButton.setOnClickListener(v -> openStory(attractionId));
        }

        boolean failed = state.isVisitSaveFailed();
        visitSaveFailedNotice.setVisibility(failed ? View.VISIBLE : View.GONE);
        retryVisitButton.setVisibility(failed ? View.VISIBLE : View.GONE);
    }

    private void openStory(String attractionId) {
        viewModel.onStoryRequested();
        Bundle arguments = new Bundle();
        arguments.putString(NearbyAttractionsFragment.ARG_ATTRACTION_ID, attractionId);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_exploration_to_story, arguments);
    }

    /**
     * Three levels of direction, degrading one step at a time.
     *
     * <p>An arrow needs a position, a destination and a working compass. Take the compass away and
     * the cardinal direction is still true and still useful, so it is shown without an arrow rather
     * than replaced by nothing — a user told "the museum is north-east of you" can act on that.
     * Only when the position itself is unknown does the whole line become unavailable.
     *
     * <p>The direction is always stated in words. The arrow is a second, visual rendering of the
     * same fact, never the only one, which is what keeps the screen usable without sight.
     */
    private void renderDirection(ExplorationUiState state) {
        if (!state.hasCardinal()) {
            directionArrow.setVisibility(View.GONE);
            directionView.setText(remainingUnavailable
                    ? R.string.exploration_remaining_unavailable
                    : R.string.exploration_direction_unavailable);
            announceDirection(null);
            return;
        }
        String cardinal = getString(DirectionLabels.cardinalOf(state.getCardinal()));
        if (state.hasDirectionArrow()) {
            directionArrow.setVisibility(View.VISIBLE);
            directionArrow.setRotation((float) state.getRelativeBearingDegrees());
            directionView.setText(getString(R.string.direction_towards, cardinal));
        } else {
            directionArrow.setVisibility(View.GONE);
            directionView.setText(getString(R.string.direction_no_compass, cardinal));
        }
        announceDirection(state.getCardinal());
    }

    /**
     * Says the direction when it changes, and only then.
     *
     * <p>Deliberately not a live region: the compass updates continuously, and a region would
     * make a screen reader talk over itself and over everything else on the screen. See
     * {@link CardinalAnnouncer}.
     */
    private void announceDirection(@Nullable CardinalDirection cardinal) {
        if (!directionAnnouncer.shouldAnnounce(cardinal)) {
            return;
        }
        directionView.announceForAccessibility(directionView.getText());
    }

    private String stateLabel(ExplorationUiState state) {
        if (state.isRetrySaveVisible()) {
            return getString(R.string.exploration_save_failed);
        }
        switch (state.getState()) {
            case STARTING:
                return getString(R.string.exploration_state_starting);
            case ACTIVE:
                return state.getUnavailableReason() == null
                        ? getString(R.string.exploration_state_active)
                        : unavailableLabel(state.getUnavailableReason());
            case PAUSED:
                return pausedLabel(state.getPauseReason());
            case STOPPING:
                return getString(R.string.exploration_state_stopping);
            case COMPLETED:
                return getString(R.string.exploration_state_completed);
            default:
                return getString(R.string.exploration_state_idle);
        }
    }

    private String pausedLabel(PauseReason reason) {
        if (reason == PauseReason.USER_REQUESTED) { return getString(R.string.theme_paused_user); }
        if (reason == PauseReason.PERMISSION_LOST) {
            return getString(R.string.exploration_paused_permission);
        }
        if (reason == PauseReason.PROVIDER_LOST) {
            return getString(R.string.exploration_paused_provider);
        }
        if (reason == PauseReason.PROCESS_INTERRUPTED) {
            return getString(R.string.exploration_paused_interrupted);
        }
        return getString(R.string.exploration_paused_background);
    }

    private String unavailableLabel(LocationSource.Reason reason) {
        switch (reason) {
            case PERMISSION_DENIED:
                return getString(R.string.exploration_unavailable_permission);
            case APPROXIMATE_ONLY:
                return getString(R.string.exploration_unavailable_approximate);
            case PROVIDER_DISABLED:
                return getString(R.string.exploration_unavailable_provider);
            default:
                return getString(R.string.exploration_unavailable_no_fix);
        }
    }

    private String formatDuration(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes);
        return getString(R.string.exploration_duration_format, minutes, seconds);
    }

    /** Finish saves the journey; Discard removes it. Both stop collection immediately. */
    private void showStopDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exploration_stop_title)
                .setMessage(R.string.exploration_stop_message)
                .setNeutralButton(R.string.cancel, null)
                .setNegativeButton(R.string.exploration_discard, (dialog, which) ->
                        viewModel.onDiscardRequested())
                .setPositiveButton(R.string.exploration_finish, (dialog, which) ->
                        viewModel.onStopRequested())
                .show();
    }

    private boolean framedWalk;

    private void renderMap(ExplorationMapState state) {
        map.setOrigin(state.getPosition());
        map.setDestination(state.getDestination());
        map.setRoute(state.getSegments());
        // Frame the walk once, when both ends are known; after that the user owns the viewport.
        if (!framedWalk && state.getPosition() != null && state.getDestination() != null) {
            map.frameWalk(state.getPosition(), state.getDestination());
            framedWalk = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        themeModel.refresh();
        map.onResume();
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }
}
