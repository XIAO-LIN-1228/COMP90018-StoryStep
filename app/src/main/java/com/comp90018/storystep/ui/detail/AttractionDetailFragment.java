package com.comp90018.storystep.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.ui.CategoryLabels;
import com.comp90018.storystep.ui.AttractionArtwork;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.google.android.material.chip.Chip;
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
    private Chip venueTypeChip;
    private ImageView imageView;

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
        imageView = view.findViewById(R.id.detail_image);

        view.findViewById(R.id.detail_back_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.detail_save_button).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.saved_title, Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.detail_start_button).setOnClickListener(v -> showStartConfirmation());

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(AttractionDetailViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);

        Bundle arguments = getArguments();
        String attractionId = arguments == null
                ? null
                : arguments.getString(NearbyAttractionsFragment.ARG_ATTRACTION_ID);
        viewModel.loadIfNeeded(attractionId);
    }

    private void render(AttractionDetailUiState state) {
        Attraction attraction = state.getAttraction();
        if (state.getStatus() != AttractionDetailUiState.Status.CONTENT || attraction == null) {
            boolean failed = state.getStatus() == AttractionDetailUiState.Status.ERROR;
            nameView.setText(failed ? getString(R.string.detail_error) : "");
            categoryView.setText("");
            descriptionView.setText("");
            venueTypeChip.setVisibility(View.GONE);
            return;
        }
        nameView.setText(attraction.getName());
        categoryView.setText(CategoryLabels.labelOf(attraction.getCategory()));
        descriptionView.setText(attraction.getDescription());
        imageView.setImageResource(AttractionArtwork.heroDrawableFor(attraction.getId()));
        venueTypeChip.setVisibility(View.VISIBLE);
        venueTypeChip.setText(attraction.isIndoor()
                ? R.string.attraction_indoor
                : R.string.attraction_outdoor);
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
