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
import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.ui.DistanceText;
import com.comp90018.storystep.ui.HistoryText;
import com.comp90018.storystep.ui.TouchTargets;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.WeatherText;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.comp90018.storystep.ui.widget.PlaceMapView;

/** One recorded journey: its totals, its route, and the places it visited. */
public class JourneyDetailFragment extends Fragment {

    private JourneyDetailViewModel viewModel;

    private View content;
    private ProgressBar loading;
    private TextView status;
    private TextView title;
    private TextView date;
    private TextView stats;
    private TextView statDuration;
    private TextView statDistance;
    private TextView statStories;
    private TextView label;
    private TextView movement;
    private TextView weather;
    private TextView routeCaption;
    private TextView placesHeading;
    private PlaceMapView route;
    private LinearLayout stops;
    private TextView who;
    private TextView whenWhere;
    private View publishCard;
    private android.widget.EditText titleInput;
    private LinearLayout photoStrip;
    private View addPhotos;
    private com.google.android.material.button.MaterialButton publishButton;
    private JourneyPhotoPager photos;

    private final androidx.activity.result.ActivityResultLauncher<
            androidx.activity.result.PickVisualMediaRequest> pickPhotos =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts
                            .PickMultipleVisualMedia(8),
                    uris -> {
                        if (uris != null && !uris.isEmpty()) {
                            viewModel.addPhotos(uris);
                        }
                    });

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
        statDuration = view.findViewById(R.id.stat_duration);
        statDistance = view.findViewById(R.id.stat_distance);
        statStories = view.findViewById(R.id.stat_stories);
        label = view.findViewById(R.id.journey_detail_label);
        movement = view.findViewById(R.id.journey_detail_movement);
        weather = view.findViewById(R.id.journey_detail_weather);
        route = view.findViewById(R.id.journey_detail_route);
        routeCaption = view.findViewById(R.id.journey_detail_route_caption);
        placesHeading = view.findViewById(R.id.journey_detail_places_heading);
        stops = view.findViewById(R.id.journey_detail_stops);
        who = view.findViewById(R.id.journey_who);
        whenWhere = view.findViewById(R.id.journey_when_where);
        publishCard = view.findViewById(R.id.journey_publish_card);
        titleInput = view.findViewById(R.id.journey_title_input);
        photoStrip = view.findViewById(R.id.journey_photo_strip);
        addPhotos = view.findViewById(R.id.journey_add_photos);
        publishButton = view.findViewById(R.id.journey_publish_button);
        photos = view.findViewById(R.id.journey_detail_photos);
        addPhotos.setOnClickListener(v -> pickPhotos.launch(
                new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(androidx.activity.result.contract
                                .ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        publishButton.setOnClickListener(v -> {
            com.comp90018.storystep.di.AppContainer container =
                    ((com.comp90018.storystep.StoryStepApplication)
                            requireActivity().getApplication()).getAppContainer();
            viewModel.publish(container.getApplicationContext(), container.getIoExecutor(),
                    container.getMainExecutor(), sessionId(),
                    titleInput.getText() == null ? "" : titleInput.getText().toString());
        });

        TouchTargets.expand(view.findViewById(R.id.journey_detail_back));
        view.findViewById(R.id.journey_detail_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.explore_again_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_journey_detail_to_exploration));

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(JourneyDetailViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getChosenPhotos().observe(getViewLifecycleOwner(), this::renderChosenPhotos);
        viewModel.isPublishing().observe(getViewLifecycleOwner(), busy -> {
            publishButton.setEnabled(!busy);
            publishButton.setText(busy ? R.string.journey_publishing : R.string.journey_publish);
        });
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

        title.setText(summary.isPublished() ? summary.getTitle()
                : summary.getDestinationName() == null
                ? getString(R.string.history_untitled)
                : summary.getDestinationName());
        String when = HistoryText.formatDateTime(getResources(), summary.getStartWallClockMillis());
        date.setText(when);
        bindIdentity(summary, when);
        publishCard.setVisibility(summary.isPublished() ? View.GONE : View.VISIBLE);
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        for (String name : summary.getPhotoFiles()) {
            files.add(com.comp90018.storystep.platform.media.JourneyPhotoStore.file(
                    requireContext(), summary.getId(), name));
        }
        photos.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
        photos.setPhotos(files);
        // The figures are the content of this screen: each gets its own column with the
        // label above it, rather than one line of text with separators.
        String duration = HistoryText.formatDuration(getResources(), summary.getActiveDurationMillis());
        String distance = DistanceText.format(getResources(), summary.getDistanceMeters());
        stats.setText(getString(R.string.history_stats_format,
                duration + "  ·  " + distance,
                HistoryText.formatVisitCount(getResources(), summary.getVisitCount())));
        statDuration.setText(duration);
        statDistance.setText(distance);
        statStories.setText(String.valueOf(summary.getVisitCount()));

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

    /** The identity line: who, then when and where, matching the card in the list. */
    private void bindIdentity(SessionHistoryEntry summary, String when) {
        String you = getString(R.string.journey_you);
        String went = getString(R.string.journey_went_for_a_walk, you);
        android.text.SpannableString styled = new android.text.SpannableString(went);
        styled.setSpan(new android.text.style.ForegroundColorSpan(
                        androidx.core.content.ContextCompat.getColor(
                                requireContext(), R.color.story_signal_ink)),
                0, you.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        who.setText(styled);
        String where = summary.getPlaceName() != null ? summary.getPlaceName()
                : summary.getDestinationName();
        whenWhere.setText(where == null ? when
                : getString(R.string.journey_when_where, when, where));
    }

    /** The thumbnails of the photographs chosen so far, each removable, before the add tile. */
    private void renderChosenPhotos(java.util.List<android.net.Uri> chosen) {
        for (int i = photoStrip.getChildCount() - 1; i >= 0; i--) {
            if (photoStrip.getChildAt(i) != addPhotos) {
                photoStrip.removeViewAt(i);
            }
        }
        int size = Math.round(76 * getResources().getDisplayMetrics().density);
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < chosen.size(); i++) {
            android.net.Uri uri = chosen.get(i);
            com.google.android.material.imageview.ShapeableImageView thumb =
                    new com.google.android.material.imageview.ShapeableImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(gap);
            thumb.setLayoutParams(params);
            thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            thumb.setShapeAppearanceModel(thumb.getShapeAppearanceModel().toBuilder()
                    .setAllCornerSizes(12 * getResources().getDisplayMetrics().density).build());
            thumb.setContentDescription(getString(R.string.journey_remove_photo));
            com.bumptech.glide.Glide.with(thumb).load(uri).centerCrop().into(thumb);
            thumb.setOnClickListener(v -> viewModel.removePhoto(uri));
            photoStrip.addView(thumb, i);
        }
    }

    private void bindRoute(SessionHistoryDetail detail) {
        if (!detail.hasRoute()) {
            route.setVisibility(View.GONE);
            routeCaption.setText(R.string.history_route_none);
            return;
        }
        route.setVisibility(View.VISIBLE);
        // The recorded route on the real map, one line per segment, framed to fit. The map is
        // looked at here, not driven, so it does not steal the page's scroll.
        java.util.List<java.util.List<com.comp90018.storystep.domain.model.GeoPoint>> segments =
                new java.util.ArrayList<>();
        for (com.comp90018.storystep.domain.model.RouteSegment segment : detail.getSegments()) {
            java.util.List<com.comp90018.storystep.domain.model.GeoPoint> points =
                    new java.util.ArrayList<>();
            for (com.comp90018.storystep.domain.model.RoutePoint point : segment.getPoints()) {
                points.add(point.getPosition());
            }
            segments.add(points);
        }
        route.setStatic(true);
        route.setRoute(segments);
        route.frameRoute(segments);
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

    @Override
    public void onResume() {
        super.onResume();
        route.onResume();
    }

    @Override
    public void onPause() {
        route.onPause();
        super.onPause();
    }
}
