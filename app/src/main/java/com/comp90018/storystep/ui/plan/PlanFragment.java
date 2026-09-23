package com.comp90018.storystep.ui.plan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.StoryStepApplication;
import com.comp90018.storystep.application.WalkPlanner;
import com.comp90018.storystep.di.AppContainer;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.plan.WalkPlan;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.HistoryText;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.comp90018.storystep.ui.widget.PlaceMapView;

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
        if (origin != null) {
            map.setOrigin(origin);
        }
        subtitle = view.findViewById(R.id.plan_subtitle);
        empty = view.findViewById(R.id.plan_empty);
        clear = view.findViewById(R.id.plan_clear);
        startButton = view.findViewById(R.id.plan_start);
        list = view.findViewById(R.id.plan_stops);
        adapter = new PlanStopAdapter(this);
        list.setAdapter(adapter);

        clear.setOnClickListener(v -> planner.clear());
        view.findViewById(R.id.plan_add).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_nearby));
        startButton.setOnClickListener(v -> startWalking());
        planner.addObserver(this);
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
        startButton.setEnabled(any);
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
        // Reordered from where the walker actually is, then walked in that order.
        planner.setPlan(plan.orderedFrom(origin));
        container().getExplorationCoordinator().requestStart(planner.plan().first());
        NavHostFragment.findNavController(this).navigate(R.id.action_plan_to_exploration);
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
