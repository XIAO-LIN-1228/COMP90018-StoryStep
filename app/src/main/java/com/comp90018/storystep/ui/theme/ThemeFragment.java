package com.comp90018.storystep.ui.theme;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.data.repository.ThemeCatalog;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.theme.*;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.journey.JourneysFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Catalog, active progress and historical results share the same durable content snapshot. */
public final class ThemeFragment extends Fragment {
    public static final String ARG_SESSION_ID = "themeSessionId";
    private ThemeViewModel model;
    private boolean resumeRequested;
    private ActivityResultLauncher<String[]> permissions;
    private ThemeProgress renderedProgress;
    private String renderedDestination;
    private Boolean renderedEditable;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        resumeRequested = state != null && state.getBoolean("themeResume");
        permissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (access().isGranted()) { performRequestedAction(); }
            else { showPermissionHelp(); }
        });
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); state.putBoolean("themeResume", resumeRequested);
    }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_theme, parent, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        renderedEditable = null; renderedProgress = null; renderedDestination = null;
        androidx.core.view.ViewCompat.setAccessibilityHeading(view.findViewById(R.id.theme_title), true);
        model = new ViewModelProvider(this, ViewModelFactory.from(this)).get(ThemeViewModel.class);
        model.showSession(getArguments() == null ? null : getArguments().getString(ARG_SESSION_ID));
        view.findViewById(R.id.theme_back).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.theme_retry).setOnClickListener(v -> model.refresh());
        view.findViewById(R.id.theme_start).setOnClickListener(v -> requestCollection(false));
        view.findViewById(R.id.theme_directions).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.explorationFragment));
        view.findViewById(R.id.theme_pause).setOnClickListener(v -> {
            if (coordinator().summary().getState() == SessionState.ACTIVE) { model.pauseOrResume(); }
            else { requestCollection(true); }
        });
        view.findViewById(R.id.theme_finish).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.theme_finish)
                        .setMessage(R.string.theme_finish_message).setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.exploration_finish, (dialog, which) -> model.finish()).show());
        view.findViewById(R.id.theme_results).setOnClickListener(v -> {
            ThemeProgress progress = model.getState().getValue().progress;
            if (progress == null) { return; }
            Bundle args = new Bundle(); args.putString(JourneysFragment.ARG_SESSION_ID, progress.sessionId);
            NavHostFragment.findNavController(this).navigate(R.id.journeyDetailFragment, args);
        });
        model.getState().observe(getViewLifecycleOwner(), this::render);
    }
    @Override public void onResume() {
        super.onResume(); coordinator().onLocationAccessChanged(access()); model.refresh();
    }
    private ExplorationCoordinator coordinator() {
        return ((StoryStepApplication) requireActivity().getApplication()).getAppContainer().getExplorationCoordinator();
    }
    private LocationAccess access() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) { return LocationAccess.PRECISE; }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) { return LocationAccess.APPROXIMATE; }
        return LocationAccess.DENIED;
    }
    private void requestCollection(boolean resume) {
        resumeRequested = resume;
        if (access().isGranted()) { performRequestedAction(); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.settings_location)
                .setMessage(R.string.theme_location_explanation).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.theme_continue, (dialog, which) -> permissions.launch(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})).show();
    }
    private void performRequestedAction() {
        coordinator().onLocationAccessChanged(access());
        android.location.LocationManager manager = (android.location.LocationManager)
                requireContext().getSystemService(android.content.Context.LOCATION_SERVICE);
        if (manager == null || !androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)) {
            new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.theme_location_services)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.settings_location,
                            (dialog, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show();
            return;
        }
        if (resumeRequested) { model.pauseOrResume(); } else { model.start(); }
    }
    private void showPermissionHelp() {
        new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.theme_permission_denied)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.settings_location,
                        (dialog, which) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + requireContext().getPackageName())))).show();
    }
    private void render(ThemeViewModel.State state) {
        View view = requireView();
        ThemeProgress progress = state.progress;
        ThemeDefinition theme = progress == null ? ThemeCatalog.parkville() : progress.theme;
        ((TextView) view.findViewById(R.id.theme_title)).setText(theme.title);
        ((TextView) view.findViewById(R.id.theme_description)).setText(theme.description);
        ((TextView) view.findViewById(R.id.theme_progress)).setText(progress == null
                ? getString(R.string.theme_preview, theme.stops.size())
                : getString(R.string.theme_progress, progress.arrivedCount(), theme.stops.size(),
                        progress.skippedCount(), progress.pendingCount()));
        int message = state.explorationFailed ? R.string.theme_exploration_error
                : state.failed ? R.string.theme_error : state.loading || state.busy ? R.string.theme_loading
                : progress == null ? (state.historical ? R.string.theme_none
                    : state.session.getState().hasSession() ? R.string.theme_other_session : R.string.theme_ready)
                : progress.finished ? (progress.allVisited() ? R.string.theme_complete : R.string.theme_partial)
                : state.session.getState() == SessionState.PAUSED ? R.string.theme_paused
                : progress.pendingCount() == 0 ? R.string.theme_stations_resolved : R.string.theme_active;
        ((TextView) view.findViewById(R.id.theme_status)).setText(message);
        button(view, R.id.theme_retry, state.failed && !state.explorationFailed, !state.busy);
        button(view, R.id.theme_start, !state.historical && !state.session.getState().hasSession(), state.canStart());
        // The dashboard also provides stop-save retry and recovery for an ordinary active journey.
        button(view, R.id.theme_directions, !state.historical && state.session.getState().hasSession(), !state.loading);
        button(view, R.id.theme_pause, progress != null && !progress.finished && !state.historical, state.editable());
        ((TextView) view.findViewById(R.id.theme_pause)).setText(state.session.getState() == SessionState.PAUSED
                ? R.string.exploration_resume : R.string.theme_pause);
        button(view, R.id.theme_finish, progress != null && !progress.finished && !state.historical, state.editable());
        button(view, R.id.theme_results, progress != null && progress.finished && !state.historical, !state.loading);
        if (renderedEditable == null || renderedProgress != progress || renderedEditable != state.editable()
                || !java.util.Objects.equals(renderedDestination, state.destinationId)) {
            renderStops(theme, state);
            renderedProgress = progress; renderedEditable = state.editable(); renderedDestination = state.destinationId;
        }
    }
    private void renderStops(ThemeDefinition theme, ThemeViewModel.State state) {
        LinearLayout list = requireView().findViewById(R.id.theme_stops);
        list.removeAllViews();
        if (state.historical && state.progress == null) { return; }
        int index = 0;
        for (ThemeStop stop : theme.stops) {
            View row = getLayoutInflater().inflate(R.layout.item_theme_stop, list, false);
            // Repeated layout IDs must not restore another station's selection on rotation.
            // The repository snapshot is the sole source of restored answer/progress state.
            row.setSaveFromParentEnabled(false);
            androidx.core.view.ViewCompat.setAccessibilityHeading(row.findViewById(R.id.theme_stop_name), true);
            ((TextView) row.findViewById(R.id.theme_stop_name)).setText(getString(R.string.theme_stop_title, ++index, stop.name));
            ThemeProgress.Status status = state.progress == null ? ThemeProgress.Status.PENDING : state.progress.status(stop.id);
            ((TextView) row.findViewById(R.id.theme_stop_status)).setText(status == ThemeProgress.Status.ARRIVED
                    ? R.string.theme_arrived : status == ThemeProgress.Status.SKIPPED ? R.string.theme_skipped
                    : stop.id.equals(state.destinationId) && state.progress != null ? R.string.theme_selected : R.string.theme_pending);
            button(row, R.id.theme_stop_select, state.progress != null && !state.progress.finished
                    && !state.historical && status == ThemeProgress.Status.PENDING, state.editable());
            row.findViewById(R.id.theme_stop_select).setOnClickListener(v -> model.select(stop.id));
            button(row, R.id.theme_stop_skip, state.progress != null && !state.progress.finished
                    && !state.historical && status != ThemeProgress.Status.ARRIVED, state.editable());
            ((TextView) row.findViewById(R.id.theme_stop_skip)).setText(status == ThemeProgress.Status.SKIPPED
                    ? R.string.theme_restore_stop : R.string.theme_skip);
            row.findViewById(R.id.theme_stop_skip).setOnClickListener(v -> model.skip(stop.id, status != ThemeProgress.Status.SKIPPED));
            boolean arrived = status == ThemeProgress.Status.ARRIVED;
            ((TextView) row.findViewById(R.id.theme_stop_story)).setText(arrived ? stop.story : getString(R.string.theme_story_locked));
            ((TextView) row.findViewById(R.id.theme_stop_source)).setText(getString(R.string.theme_source, stop.sourceUrl));
            TextView question = row.findViewById(R.id.theme_stop_question);
            question.setText(stop.question); question.setVisibility(arrived ? View.VISIBLE : View.GONE);
            RadioGroup choices = row.findViewById(R.id.theme_stop_options);
            choices.setVisibility(arrived ? View.VISIBLE : View.GONE);
            Integer answer = state.progress == null ? null : state.progress.answer(stop.id);
            for (int i = 0; i < stop.options.size(); i++) {
                RadioButton choice = new RadioButton(requireContext());
                choice.setId(View.generateViewId()); choice.setSaveEnabled(false);
                choice.setText(stop.options.get(i)); choice.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
                choice.setEnabled(arrived && state.editable()); choices.addView(choice);
                if (answer != null && answer == i) { choice.setChecked(true); }
                final int option = i;
                choice.setOnClickListener(v -> model.answer(stop.id, option));
            }
            TextView feedback = row.findViewById(R.id.theme_stop_answer);
            feedback.setText(answer == null ? R.string.theme_question_optional : answer == stop.correctAnswer
                    ? R.string.theme_answer_correct : R.string.theme_answer_incorrect);
            feedback.setVisibility(arrived ? View.VISIBLE : View.GONE);
            list.addView(row);
        }
    }
    private static void button(View parent, int id, boolean visible, boolean enabled) {
        View button = parent.findViewById(id);
        button.setVisibility(visible ? View.VISIBLE : View.GONE); button.setEnabled(enabled);
    }
}
