package com.comp90018.storystep.ui.plan;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.application.WalkPlanner;
import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.plan.WalkPlan;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationAccessPolicy;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.HistoryText;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.comp90018.storystep.ui.widget.PlaceMapView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * Planning a walk: which places, in which order, and how far that comes to.
 *
 * <p>The Start tab opens here rather than on a dashboard with no session, because deciding where
 * to go is what a walker does first. The plan is held by the process-scoped {@link WalkPlanner},
 * so adding a place from the nearby list or a detail screen and coming back finds it here.
 *
 * <p>The line drawn between stops is the plan, not a route: straight legs, no street geometry
 * and no instructions. StoryStep gives direction and distance, not turn-by-turn navigation.
 */
public class PlanFragment extends Fragment implements WalkPlanner.Observer,
        PlanStopAdapter.OnStopAction {

    private PlaceMapView map;
    private RecyclerView list;
    private PlanStopAdapter adapter;
    private TextView subtitle;
    private View empty;
    private TextView clear;
    private View startButton;
    private PlanStartViewModel startModel;
    private TextView startStatus;
    private static final String LOCATION_PREFS = "storystep.location";
    private static final String PERMISSION_REQUESTED = "location_permission_requested";

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (getView() == null) { return; }
                if (currentAccess().isGranted()) {
                    startWalking();
                } else {
                    // Explain the result, but never launch another system prompt automatically.
                    showLocationPermissionHelp();
                }
            });

    private WalkPlanner planner;
    @Nullable private GeoPoint origin;
    /** The route last worked out, and the plan it was worked out for. */
    @Nullable private com.comp90018.storystep.domain.plan.PlannedRoute route;
    @Nullable private WalkPlan routedPlan;
    private int routeGeneration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppContainer container = container();
        planner = container.getWalkPlanner();
        origin = container.getReferenceOrigin();

        map = view.findViewById(R.id.plan_map);
        map.setCoveredBottom(getResources().getDimensionPixelSize(R.dimen.sheet_peek_height));
        View sheetView = view.findViewById(R.id.plan_sheet);
        BottomSheetBehavior<View> sheet = BottomSheetBehavior.from(sheetView);
        ((View) sheetView.getParent()).addOnLayoutChangeListener(
                (parent, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int height = bottom - top;
                    if (height <= 1) { return; }
                    int peek = Math.min(getResources().getDimensionPixelSize(R.dimen.sheet_peek_height),
                            height - 1);
                    sheet.setPeekHeight(peek);
                    // The fixed footer reduces the sheet's height. Keep the middle snap between
                    // collapsed and expanded instead of letting it fall below the collapsed top.
                    sheet.setHalfExpandedRatio((1f + (float) peek / height) / 2f);
                    map.setCoveredBottom(peek);
                });
        if (origin != null) {
            map.setOrigin(origin);
        }
        subtitle = view.findViewById(R.id.plan_subtitle);
        empty = view.findViewById(R.id.plan_empty);
        clear = view.findViewById(R.id.plan_clear);
        startButton = view.findViewById(R.id.plan_start);
        startStatus = view.findViewById(R.id.plan_start_status);
        startModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(PlanStartViewModel.class);
        list = view.findViewById(R.id.plan_stops);
        adapter = new PlanStopAdapter(this);
        list.setAdapter(adapter);

        clear.setOnClickListener(v -> planner.clear());
        view.findViewById(R.id.plan_add).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_nearby));
        startButton.setOnClickListener(v -> startWalking());
        view.findViewById(R.id.plan_in_progress_action).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_exploration));
        planner.addObserver(this);
        startModel.getState().observe(getViewLifecycleOwner(), state -> {
            startButton.setEnabled(!planner.plan().isEmpty()
                    && state != PlanStartViewModel.State.STARTING);
            startStatus.setVisibility(state == PlanStartViewModel.State.STARTING
                    || state == PlanStartViewModel.State.FAILED ? View.VISIBLE : View.GONE);
            startStatus.setText(state == PlanStartViewModel.State.STARTING
                    ? R.string.plan_starting : R.string.plan_start_failed);
            if (state == PlanStartViewModel.State.READY) {
                startModel.acknowledgeResult();
                NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_exploration);
            }
        });
    }

    /**
     * A walk already running or paused, shown above the plan: planning is where walking begins,
     * so it is where a walk in progress is picked back up. Without this there is no route to an
     * unfinished walk at all, and one would sit in the database blocking the next.
     */
    private void showWalkInProgress() {
        View view = getView();
        if (view == null) {
            return;
        }
        com.comp90018.storystep.domain.model.SessionSummary summary =
                container().getExplorationCoordinator().summary();
        com.comp90018.storystep.domain.model.SessionState state =
                summary == null ? null : summary.getState();
        boolean running = state != null && state.hasSession();
        view.findViewById(R.id.plan_in_progress).setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            ((TextView) view.findViewById(R.id.plan_in_progress_text)).setText(
                    state == com.comp90018.storystep.domain.model.SessionState.PAUSED
                            ? R.string.plan_walk_paused : R.string.plan_walk_running);
        }
    }

    @Override
    public void onPlanChanged(WalkPlan plan) {
        if (getView() == null) {
            return;
        }
        adapter.setStops(plan.getStops(), origin);
        boolean any = !plan.isEmpty();
        empty.setVisibility(any ? View.GONE : View.VISIBLE);
        list.setVisibility(any ? View.VISIBLE : View.GONE);
        clear.setVisibility(any ? View.VISIBLE : View.GONE);
        startButton.setEnabled(any && startModel.getState().getValue()
                != PlanStartViewModel.State.STARTING);
        subtitle.setText(any ? summary(plan) : getString(R.string.plan_subtitle_empty));

        List<PlaceMapView.Place> pins = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            Attraction stop = plan.getStops().get(i);
            pins.add(PlaceMapView.Place.stop(
                    stop.getId(), stop.getName(), stop.getLocation(), i + 1));
        }
        map.setPlaces(pins);
        // Straight legs at once, so the map is never empty while the router is asked; the real
        // path replaces them when it arrives.
        if (!plan.equals(routedPlan)) {
            route = null;
            routedPlan = null;
            requestRoute(plan);
        }
        drawRoute(plan);
    }

    /** Draws the routed path when there is one for this plan, and the straight legs otherwise. */
    private void drawRoute(WalkPlan plan) {
        List<List<GeoPoint>> path = new ArrayList<>();
        if (plan.size() > 0) {
            path.add(route != null && plan.equals(routedPlan)
                    ? route.getPath() : plan.path(origin));
        }
        map.setRoute(path);
        if (!path.isEmpty()) {
            map.frameRoute(path);
        }
    }

    /**
     * Asks the router for the walk through this plan's stops. The answer is the path, the
     * distance and the walking time; the plan adds its own stay at each stop. A router that
     * cannot answer leaves the straight legs in place, which is what the plan showed before.
     */
    private void requestRoute(WalkPlan plan) {
        if (plan.isEmpty() || origin == null) {
            return;
        }
        AppContainer container = container();
        com.comp90018.storystep.data.remote.WalkRouteSource source = container.getWalkRouteSource();
        if (source == null) {
            return;
        }
        int generation = ++routeGeneration;
        List<GeoPoint> points = plan.path(origin);
        container.getIoExecutor().execute(() -> {
            com.comp90018.storystep.domain.plan.PlannedRoute walked = source.walkThrough(points);
            container.getMainExecutor().execute(() -> {
                if (generation != routeGeneration || getView() == null || walked == null) {
                    return;
                }
                route = walked;
                routedPlan = plan;
                subtitle.setText(summary(plan));
                drawRoute(plan);
            });
        });
    }

    /**
     * "3 stops · about 2.1 km · around 40 min". The walking figures come from the router when
     * it has answered and from the plan's straight legs until then; the stay at each stop is
     * the plan's either way. Every figure is approximate and the line says so.
     */
    private String summary(WalkPlan plan) {
        String stops = getString(plan.size() == 1
                ? R.string.plan_stop_count : R.string.plan_stop_count_plural, plan.size());
        boolean routed = route != null && plan.equals(routedPlan);
        double metres = routed ? route.getDistanceMeters() : plan.distanceMeters(origin);
        long millis = routed
                ? route.getDurationMillis() + WalkPlan.DWELL_MILLIS_PER_STOP * plan.size()
                : plan.durationMillis(origin);
        return getString(R.string.plan_subtitle, stops,
                DistanceText.format(getResources(), metres),
                HistoryText.formatDuration(getResources(), millis));
    }

    private void startWalking() {
        WalkPlan plan = planner.plan();
        Attraction first = plan.first();
        if (first == null) {
            return;
        }
        if (container().getExplorationCoordinator().summary().getState().hasSession()) {
            NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_exploration);
            return;
        }
        LocationAccess access = currentAccess();
        if (!access.isGranted()) {
            showLocationPermissionHelp();
            return;
        }
        LocationManager manager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.plan_location_services)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.nearby_open_settings, (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .show();
            return;
        }
        // Reordered from where the walker actually is, then walked in that order.
        planner.setPlan(plan.orderedFrom(origin));
        startModel.start(planner.plan().first(), access);
    }

    private LocationAccess currentAccess() {
        Context context = requireContext();
        return LocationAccessPolicy.evaluate(
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED,
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION),
                context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
                        .getBoolean(PERMISSION_REQUESTED, false));
    }

    private void showLocationPermissionHelp() {
        boolean canRequest = currentAccess().canRequest();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_location)
                .setMessage(canRequest ? R.string.plan_location_permission
                        : R.string.nearby_permission_permanently_denied)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(canRequest ? R.string.nearby_grant_access
                        : R.string.nearby_open_settings, (dialog, which) -> {
                    if (canRequest) {
                        requireContext().getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
                                .edit().putBoolean(PERMISSION_REQUESTED, true).apply();
                        permissions.launch(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION});
                    } else {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", requireContext().getPackageName(), null)));
                    }
                }).show();
    }

    @Override
    public void onMove(String attractionId, int by) {
        planner.move(attractionId, by);
    }

    @Override
    public void onRemove(String attractionId) {
        planner.remove(attractionId);
    }

    @Override
    public void onOpen(Attraction stop) {
        Bundle arguments = new Bundle();
        arguments.putString(NearbyAttractionsFragment.ARG_ATTRACTION_ID, stop.getId());
        NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_detail, arguments);
    }

    private AppContainer container() {
        return ((StoryStepApplication) requireActivity().getApplication()).getAppContainer();
    }

    @Override
    public void onResume() {
        super.onResume();
        showWalkInProgress();
        map.onResume();
    }

    @Override
    public void onPause() {
        map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        planner.removeObserver(this);
        super.onDestroyView();
    }
}
