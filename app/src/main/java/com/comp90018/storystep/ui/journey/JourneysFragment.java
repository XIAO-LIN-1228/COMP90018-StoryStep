package com.comp90018.storystep.ui.journey;

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
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** The user's recorded journeys, read from the session tables. */
public class JourneysFragment extends Fragment
        implements JourneyAdapter.OnJourneySelected, JourneyAdapter.OnJourneyDeleteRequested {

    /** Bundle key for the session a detail screen should open. */
    public static final String ARG_SESSION_ID = "sessionId";

    private JourneysViewModel viewModel;
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
        adapter = new JourneyAdapter(this, this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(JourneysViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getDeleteFailed().observe(getViewLifecycleOwner(), this::reportDeleteFailure);
        viewModel.loadIfNeeded();
    }

    private void render(JourneysUiState state) {
        JourneysUiState.Status current = state.getStatus();
        loading.setVisibility(current == JourneysUiState.Status.LOADING ? View.VISIBLE : View.GONE);
        list.setVisibility(current == JourneysUiState.Status.CONTENT ? View.VISIBLE : View.GONE);

        boolean hasMessage = current == JourneysUiState.Status.EMPTY
                || current == JourneysUiState.Status.ERROR;
        status.setVisibility(hasMessage ? View.VISIBLE : View.GONE);
        if (hasMessage) {
            status.setText(current == JourneysUiState.Status.EMPTY
                    ? R.string.history_empty : R.string.history_error);
        }
        adapter.submitList(state.getJourneys());
    }

    private void reportDeleteFailure(Boolean failed) {
        if (!Boolean.TRUE.equals(failed)) {
            return;
        }
        Toast.makeText(requireContext(), R.string.history_delete_failed, Toast.LENGTH_SHORT).show();
        viewModel.consumeDeleteFailure();
    }

    @Override
    public void onJourneySelected(SessionHistoryEntry journey) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SESSION_ID, journey.getId());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_journeys_to_detail, arguments);
    }

    @Override
    public void onJourneyDeleteRequested(SessionHistoryEntry journey) {
        // Confirmed before anything is written, and cancelling leaves the record untouched: this
        // is the user's own history and there is no undo behind it.
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_delete_title)
                .setMessage(R.string.history_delete_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.history_delete_action, (dialog, which) ->
                        viewModel.onDeleteConfirmed(journey.getId()))
                .show();
    }

    @Override
    public void onDestroyView() {
        list.setAdapter(null);
        super.onDestroyView();
    }
}
