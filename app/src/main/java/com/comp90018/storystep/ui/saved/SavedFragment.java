package com.comp90018.storystep.ui.saved;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

/**
 * The places the user has kept.
 *
 * <p>Backed by the saved store rather than by the nearby list it used to borrow, so what it shows
 * is what was actually saved — offline included.
 */
public class SavedFragment extends Fragment
        implements AttractionAdapter.OnAttractionSelected, AttractionAdapter.OnSaveToggled {

    private SavedViewModel viewModel;
    private AttractionAdapter adapter;
    private RecyclerView list;
    private ProgressBar loading;
    private TextView message;

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
        view.findViewById(R.id.saved_back).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp());
        list = view.findViewById(R.id.saved_list);
        loading = view.findViewById(R.id.saved_loading);
        message = view.findViewById(R.id.saved_message);
        adapter = new AttractionAdapter(this, this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(SavedViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSaveFailed().observe(getViewLifecycleOwner(), this::reportRemovalFailure);
        viewModel.loadIfNeeded();
    }

    private void render(SavedUiState state) {
        SavedUiState.Status status = state.getStatus();
        loading.setVisibility(status == SavedUiState.Status.LOADING ? View.VISIBLE : View.GONE);
        list.setVisibility(status == SavedUiState.Status.CONTENT ? View.VISIBLE : View.GONE);

        boolean hasMessage = status == SavedUiState.Status.EMPTY
                || status == SavedUiState.Status.ERROR;
        message.setVisibility(hasMessage ? View.VISIBLE : View.GONE);
        if (hasMessage) {
            message.setText(status == SavedUiState.Status.EMPTY
                    ? R.string.saved_empty
                    : R.string.saved_error);
        }
        adapter.submitList(state.getItems());
    }

    private void reportRemovalFailure(Boolean failed) {
        if (!Boolean.TRUE.equals(failed)) {
            return;
        }
        Toast.makeText(requireContext(), R.string.detail_save_failed, Toast.LENGTH_SHORT).show();
        viewModel.consumeSaveFailure();
    }

    @Override
    public void onAttractionSelected(AttractionListItem item) {
        Bundle arguments = new Bundle();
        arguments.putString(NearbyAttractionsFragment.ARG_ATTRACTION_ID, item.getId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_saved_to_detail, arguments);
    }

    @Override
    public void onSaveToggled(AttractionListItem item) {
        viewModel.onSaveToggleRequested(item);
    }

    @Override
    public void onDestroyView() {
        list.setAdapter(null);
        super.onDestroyView();
    }
}
