package com.comp90018.storystep.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.ui.AttributionText;
import com.comp90018.storystep.ui.CategoryLabels;
import com.comp90018.storystep.ui.AttractionArtwork;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Details of one attraction. The identifier arrives as a plain Bundle argument; Safe Args is
 * intentionally not used, because it would require the Kotlin Gradle plugin.
 */
public class AttractionDetailFragment extends Fragment {

    private AttractionDetailViewModel viewModel;

    private TextView nameView;
    private TextView categoryView;
    private TextView descriptionView;
    // Indoor/outdoor reads as a filled pill beside the category rather than as a
    // Material chip competing with the name; a TextView carries the same text API.
    private TextView venueTypeChip;
    private TextView attributionView;
    private ImageView imageView;
    private ImageButton saveButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attraction_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameView = view.findViewById(R.id.detail_name);
        categoryView = view.findViewById(R.id.detail_category);
        descriptionView = view.findViewById(R.id.detail_description);
        venueTypeChip = view.findViewById(R.id.detail_venue_type);
        attributionView = view.findViewById(R.id.detail_attribution);
        imageView = view.findViewById(R.id.detail_image);
        saveButton = view.findViewById(R.id.detail_save_button);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(AttractionDetailViewModel.class);

        view.findViewById(R.id.detail_back_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        saveButton.setOnClickListener(v -> viewModel.onSaveToggleRequested());
        view.findViewById(R.id.detail_start_button).setOnClickListener(v -> showStartConfirmation());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSaveFailed().observe(getViewLifecycleOwner(), this::reportSaveFailure);

        Bundle arguments = getArguments();
        String attractionId = arguments == null
                ? null
                : arguments.getString(NearbyAttractionsFragment.ARG_ATTRACTION_ID);
        viewModel.loadIfNeeded(attractionId);
    }

    private void render(AttractionDetailUiState state) {
        Attraction attraction = state.getAttraction();
        renderSaveButton(state);
        if (state.getStatus() != AttractionDetailUiState.Status.CONTENT || attraction == null) {
            boolean failed = state.getStatus() == AttractionDetailUiState.Status.ERROR;
            nameView.setText(failed ? getString(R.string.detail_error) : "");
            categoryView.setText("");
            descriptionView.setText("");
            venueTypeChip.setVisibility(View.GONE);
            attributionView.setText("");
            return;
        }
        nameView.setText(attraction.getName());
        categoryView.setText(CategoryLabels.labelOf(attraction.getCategory()));
        // A fetched place usually has no description in OpenStreetMap. Saying so is the honest
        // rendering; leaving the field blank reads as a loading failure, and writing a sentence
        // here would be this app inventing a fact about a real place.
        if (attraction.hasDescription()) {
            descriptionView.setText(attraction.getDescription());
        } else {
            descriptionView.setText(R.string.attraction_no_description);
        }
        attributionView.setText(AttributionText.attributionFor(attraction.getProvenance()));
        imageView.setImageResource(AttractionArtwork.heroDrawableFor(attraction.getId()));
        venueTypeChip.setVisibility(View.VISIBLE);
        venueTypeChip.setText(venueTypeLabel(attraction.getIndoor()));
    }

    /**
     * The chip label, including the case where nothing is known.
     *
     * <p>An unknown classification gets its own label rather than borrowing "Outdoor". A place the
     * provider never described is not a place that was described as outdoors, and the chip is read
     * as a statement about the place.
     */
    private static int venueTypeLabel(IndoorClassification classification) {
        switch (classification) {
            case INDOOR:
                return R.string.attraction_indoor;
            case OUTDOOR:
                return R.string.attraction_outdoor;
            default:
                return R.string.attraction_indoor_unknown;
        }
    }

    /**
     * The bookmark is hidden until there is something to save, so it cannot be pressed against an
     * attraction that failed to load. Its label changes with its state — an icon alone tells a
     * screen reader nothing about which way pressing it goes.
     */
    private void renderSaveButton(AttractionDetailUiState state) {
        boolean loaded = state.getStatus() == AttractionDetailUiState.Status.CONTENT
                && state.getAttraction() != null;
        saveButton.setVisibility(loaded ? View.VISIBLE : View.GONE);
        saveButton.setImageResource(state.isSaved()
                ? R.drawable.ic_bookmark_filled
                : R.drawable.ic_bookmark_outline);
        saveButton.setContentDescription(getString(state.isSaved()
                ? R.string.detail_unsave
                : R.string.detail_save));
    }

    private void reportSaveFailure(Boolean failed) {
        if (!Boolean.TRUE.equals(failed)) {
            return;
        }
        Toast.makeText(requireContext(), R.string.detail_save_failed, Toast.LENGTH_SHORT).show();
        viewModel.consumeSaveFailure();
    }

    private void showStartConfirmation() {
        AttractionDetailUiState state = viewModel.getUiState().getValue();
        Attraction attraction = state == null ? null : state.getAttraction();
        if (attraction == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_title)
                .setMessage(getString(R.string.confirm_destination, attraction.getName()) + "\n\n"
                        + getString(R.string.confirm_location) + "\n"
                        + getString(R.string.confirm_stop))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm_start, (dialog, which) -> startExploring())
                .show();
    }

    /**
     * Opens the dashboard only once a session is genuinely running. Without location access the
     * destination is remembered and the user is returned to the nearby screen, which owns the
     * permission flow; navigating to a dashboard with no session would show an explore screen that
     * is not exploring.
     */
    private void startExploring() {
        if (viewModel.onStartExplorationRequested()) {
            NavHostFragment.findNavController(this).navigate(R.id.action_detail_to_exploration);
            return;
        }
        Toast.makeText(requireContext(), R.string.start_needs_location, Toast.LENGTH_LONG).show();
        NavHostFragment.findNavController(this)
                .popBackStack(R.id.nearbyAttractionsFragment, false);
    }
}
