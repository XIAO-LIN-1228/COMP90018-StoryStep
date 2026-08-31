package com.comp90018.storystep.ui.journey;

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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.Journey;
import com.comp90018.storystep.ui.ViewModelFactory;

/** Journey-history top-level destination backed by JourneyRepository. */
public class JourneysFragment extends Fragment implements JourneyAdapter.OnJourneySelected {

    private JourneyAdapter adapter;
    private RecyclerView list;
    private ProgressBar loading;
    private TextView status;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journeys, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.journey_list);
        loading = view.findViewById(R.id.journeys_loading);
        status = view.findViewById(R.id.journeys_status);
        adapter = new JourneyAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        JourneysViewModel viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(JourneysViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.loadIfNeeded();
    }

    private void render(JourneysUiState state) {
        boolean content = state.getStatus() == JourneysUiState.Status.CONTENT;
        loading.setVisibility(state.getStatus() == JourneysUiState.Status.LOADING
                ? View.VISIBLE : View.GONE);
        list.setVisibility(content ? View.VISIBLE : View.GONE);
        status.setVisibility(content || state.getStatus() == JourneysUiState.Status.LOADING
                ? View.GONE : View.VISIBLE);
        status.setText(state.getStatus() == JourneysUiState.Status.ERROR
                ? R.string.nearby_error : R.string.nearby_empty);
        adapter.submitList(state.getJourneys());
    }

    @Override
    public void onJourneySelected(Journey journey) {
        NavHostFragment.findNavController(this).navigate(R.id.action_journeys_to_detail);
    }

    @Override
    public void onDestroyView() {
        list.setAdapter(null);
        super.onDestroyView();
    }
}
