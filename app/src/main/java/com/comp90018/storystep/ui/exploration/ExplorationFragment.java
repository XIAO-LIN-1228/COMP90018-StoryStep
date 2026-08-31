package com.comp90018.storystep.ui.exploration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.widget.StoryMapView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Prototype foreground exploration screen; real location and heading sources plug in later. */
public class ExplorationFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exploration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        StoryMapView map = view.findViewById(R.id.exploration_map);
        map.setExplorationMode(true);

        view.findViewById(R.id.exploration_back).setOnClickListener(v -> showStopDialog());
        view.findViewById(R.id.stop_exploration_button).setOnClickListener(v -> showStopDialog());
        view.findViewById(R.id.preview_arrival_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_exploration_to_arrival));
    }

    private void showStopDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.exploration_stop_title)
                .setMessage(R.string.exploration_stop_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exploration_finish, (dialog, which) ->
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.action_exploration_to_summary))
                .show();
    }
}
