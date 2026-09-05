package com.comp90018.storystep.ui.journey;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.geo.RouteProjection;
import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.HistoryText;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.WeatherText;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.comp90018.storystep.ui.widget.RouteSketchView;

/** One recorded journey: its totals, its route, and the places it visited. */
public class JourneyDetailFragment extends Fragment {

    private JourneyDetailViewModel viewModel;

    private View content;
    private ProgressBar loading;
    private TextView status;
    private TextView title;
    private TextView date;
    private TextView stats;
    private TextView label;
    private TextView movement;
    private TextView weather;
    private TextView routeCaption;
    private TextView placesHeading;
    private RouteSketchView route;
    private LinearLayout stops;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        content = view.findViewById(R.id.journey_detail_content);
        loading = view.findViewById(R.id.journey_detail_loading);
        status = view.findViewById(R.id.journey_detail_status);
        title = view.findViewById(R.id.journey_detail_title);
        date = view.findViewById(R.id.journey_detail_date);
        stats = view.findViewById(R.id.journey_detail_stats);
        label = view.findViewById(R.id.journey_detail_label);
        movement = view.findViewById(R.id.journey_detail_movement);
        weather = view.findViewById(R.id.journey_detail_weather);
        route = view.findViewById(R.id.journey_detail_route);
        routeCaption = view.findViewById(R.id.journey_detail_route_caption);
        placesHeading = view.findViewById(R.id.journey_detail_places_heading);
        stops = view.findViewById(R.id.journey_detail_stops);

        view.findViewById(R.id.journey_detail_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.explore_again_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_journey_detail_to_exploration));

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(JourneyDetailViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.loadIfNeeded(sessionId());
    }

    @Nullable
    private String sessionId() {
        Bundle arguments = getArguments();
        return arguments == null ? null : arguments.getString(JourneysFragment.ARG_SESSION_ID);
    }

    private void render(JourneyDetailUiState state) {
        JourneyDetailUiState.Status current = state.getStatus();
        loading.setVisibility(current == JourneyDetailUiState.Status.LOADING
                ? View.VISIBLE : View.GONE);
        content.setVisibility(current == JourneyDetailUiState.Status.CONTENT
                ? View.VISIBLE : View.GONE);

        boolean hasMessage = current == JourneyDetailUiState.Status.NOT_FOUND
                || current == JourneyDetailUiState.Status.ERROR;
        status.setVisibility(hasMessage ? View.VISIBLE : View.GONE);
        if (hasMessage) {
            status.setText(current == JourneyDetailUiState.Status.NOT_FOUND
                    ? R.string.history_detail_not_found
                    : R.string.history_detail_error);
            return;
        }
        if (current == JourneyDetailUiState.Status.CONTENT) {
            bind(state.getDetail());
        }
    }

    private void bind(SessionHistoryDetail detail) {
        SessionHistoryEntry summary = detail.getSummary();

        title.setText(summary.getDestinationName() == null
                ? getString(R.string.history_untitled)
                : summary.getDestinationName());
        date.setText(HistoryText.formatDateTime(
                getResources(), summary.getStartWallClockMillis()));
        stats.setText(getString(R.string.history_stats_format,
                HistoryText.formatDuration(getResources(), summary.getActiveDurationMillis())
                        + "  ·  "
                        + DistanceText.format(getResources(), summary.getDistanceMeters()),
                HistoryText.formatVisitCount(getResources(), summary.getVisitCount())));

        if (summary.isInterrupted()) {
            label.setVisibility(View.VISIBLE);
            label.setText(R.string.history_interrupted);
        } else if (summary.hasRouteGaps()) {
            label.setVisibility(View.VISIBLE);
            label.setText(getString(R.string.history_has_gaps, summary.getSegmentCount()));
        } else {
            label.setVisibility(View.GONE);
        }

        bindRoute(detail);

        movement.setText(HistoryText.formatMovement(getResources(), detail.getMovement()));
        weather.setText(detail.hasWeather()
                ? WeatherText.formatRecorded(getResources(), detail.getWeather())
                : getString(R.string.history_weather_none));

        bindStops(detail);
    }

    private void bindRoute(SessionHistoryDetail detail) {
        if (!detail.hasRoute()) {
            route.setVisibility(View.GONE);
            routeCaption.setText(R.string.history_route_none);
            return;
        }
        route.setVisibility(View.VISIBLE);
        route.setRoute(RouteProjection.project(detail.getSegments()));
        route.setContentDescription(
                HistoryText.describeRoute(getResources(), detail.getSegments().size()));
        routeCaption.setText(R.string.history_route_caption);
    }

    private void bindStops(SessionHistoryDetail detail) {
        stops.removeAllViews();
        placesHeading.setVisibility(detail.getVisits().isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Visit visit : detail.getVisits()) {
            View row = inflater.inflate(R.layout.item_journey_stop, stops, false);
            TextView name = row.findViewById(R.id.journey_stop_name);
            TextView meta = row.findViewById(R.id.journey_stop_meta);
            name.setText(visit.getAttractionName());

            boolean hasStory = visit.getStory() != null && !visit.getStory().isEmpty();
            meta.setText(hasStory
                    ? getString(R.string.history_visit_time,
                            HistoryText.formatTime(visit.getArrivalWallClockMillis()))
                    : getString(R.string.history_no_story));
            // Only offered when there is something to open. A row that navigates to an empty
            // screen is worse than one that plainly says nothing was recorded.
            row.setClickable(hasStory);
            row.setFocusable(hasStory);
            if (hasStory) {
                row.setOnClickListener(v -> openStory(visit));
            }
            stops.addView(row);
        }
    }

    /**
     * Reopens a story from history.
     *
     * <p>The session id travels with the attraction id because the story belongs to <em>this</em>
     * walk: without it the story screen would fall back to the running session, which is either
     * absent or, worse, a different walk entirely.
     *
     * <p>Reopening records nothing. Marking a story read is an update that never inserts, so a
     * story read back from history cannot create history of its own.
     */
    private void openStory(Visit visit) {
        Bundle arguments = new Bundle();
        arguments.putString(NearbyAttractionsFragment.ARG_ATTRACTION_ID, visit.getAttractionId());
        arguments.putString(JourneysFragment.ARG_SESSION_ID, visit.getSessionId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_journey_detail_to_story, arguments);
    }
}
