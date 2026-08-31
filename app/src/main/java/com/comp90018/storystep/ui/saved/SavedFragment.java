package com.comp90018.storystep.ui.saved;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.AttractionAdapter;
import com.comp90018.storystep.ui.nearby.AttractionListItem;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsViewModel;
import com.comp90018.storystep.ui.nearby.NearbyUiState;

/** Populated prototype saved list; persistence later sits behind a dedicated repository. */
public class SavedFragment extends Fragment implements AttractionAdapter.OnAttractionSelected {

    private AttractionAdapter adapter;
    private RecyclerView list;
    private ProgressBar loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.saved_list);
        loading = view.findViewById(R.id.saved_loading);
        adapter = new AttractionAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        NearbyAttractionsViewModel viewModel = new ViewModelProvider(this,
                ViewModelFactory.from(this)).get(NearbyAttractionsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.loadIfNeeded();
    }

    private void render(NearbyUiState state) {
        loading.setVisibility(state.getStatus() == NearbyUiState.Status.LOADING
                ? View.VISIBLE : View.GONE);
        list.setVisibility(state.getStatus() == NearbyUiState.Status.CONTENT
                ? View.VISIBLE : View.GONE);
        adapter.submitList(state.getItems());
    }

    @Override
    public void onAttractionSelected(AttractionListItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(NearbyAttractionsFragment.ARG_ATTRACTION_ID, item.getId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_saved_to_detail, arguments);
    }

    @Override
    public void onDestroyView() {
        list.setAdapter(null);
        super.onDestroyView();
    }
}
