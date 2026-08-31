package com.comp90018.storystep.ui.nearby;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Start destination: the list of nearby attractions.
 *
 * <p>The Fragment renders {@link NearbyUiState} and forwards taps. It requests no permission,
 * starts no sensor or location listener, and performs no distance calculation of its own.
 */
public class NearbyAttractionsFragment extends Fragment
        implements AttractionAdapter.OnAttractionSelected {

    /** Bundle key read by {@link com.comp90018.storystep.ui.detail.AttractionDetailFragment}. */
    public static final String ARG_ATTRACTION_ID = "attractionId";

    private NearbyAttractionsViewModel viewModel;
    private AttractionAdapter adapter;

    private RecyclerView list;
    private ProgressBar loadingIndicator;
    private TextView statusMessage;
    private MaterialButton retryButton;
    private View homeMap;
    private MaterialCardView continueJourneyCard;
    private TextInputLayout searchContainer;
    private ChipGroup filterGroup;
    private TextView sectionTitle;
    private TextView seeAllButton;
    private boolean listMode;

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
        retryButton = view.findViewById(R.id.retry_button);
        homeMap = view.findViewById(R.id.home_map);
        continueJourneyCard = view.findViewById(R.id.continue_journey_card);
        searchContainer = view.findViewById(R.id.search_container);
        filterGroup = view.findViewById(R.id.filter_group);
        sectionTitle = view.findViewById(R.id.nearby_title);
        seeAllButton = view.findViewById(R.id.see_all_button);

        adapter = new AttractionAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(NearbyAttractionsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);

        retryButton.setOnClickListener(v -> viewModel.load());
        seeAllButton.setOnClickListener(v -> setListMode(!listMode));
        view.findViewById(R.id.resume_journey_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_nearby_to_exploration));
        viewModel.loadIfNeeded();
    }

    @Override
    public void onDestroyView() {
        // Release the adapter so the detached view hierarchy is not retained.
        list.setAdapter(null);
        super.onDestroyView();
    }

    private void render(NearbyUiState state) {
        boolean loading = state.getStatus() == NearbyUiState.Status.LOADING;
        boolean content = state.getStatus() == NearbyUiState.Status.CONTENT;
        boolean error = state.getStatus() == NearbyUiState.Status.ERROR;
        boolean empty = state.getStatus() == NearbyUiState.Status.EMPTY;

        loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        list.setVisibility(content ? View.VISIBLE : View.GONE);
        statusMessage.setVisibility(error || empty ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(error ? View.VISIBLE : View.GONE);

        if (error) {
            statusMessage.setText(R.string.nearby_error);
        } else if (empty) {
            statusMessage.setText(R.string.nearby_empty);
        }
        adapter.submitList(state.getItems());
    }

    @Override
    public void onAttractionSelected(AttractionListItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ATTRACTION_ID, item.getId());
        NavController navController = NavHostFragment.findNavController(this);
        navController.navigate(R.id.action_nearby_to_detail, arguments);
    }

    private void setListMode(boolean enabled) {
        listMode = enabled;
        homeMap.setVisibility(enabled ? View.GONE : View.VISIBLE);
        continueJourneyCard.setVisibility(enabled ? View.GONE : View.VISIBLE);
        searchContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        filterGroup.setVisibility(enabled ? View.VISIBLE : View.GONE);
        sectionTitle.setText(enabled ? R.string.nearby_title : R.string.home_nearby);
        seeAllButton.setText(enabled ? R.string.nav_explore : R.string.home_see_all);

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
}
