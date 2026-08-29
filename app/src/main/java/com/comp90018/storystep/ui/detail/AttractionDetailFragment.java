package com.comp90018.storystep.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.ui.CategoryLabels;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.google.android.material.chip.Chip;

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
        venueTypeChip.setVisibility(View.VISIBLE);
        venueTypeChip.setText(attraction.isIndoor()
                ? R.string.attraction_indoor
                : R.string.attraction_outdoor);
    }
}
