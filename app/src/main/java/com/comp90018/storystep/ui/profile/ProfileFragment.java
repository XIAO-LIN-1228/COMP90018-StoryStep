package com.comp90018.storystep.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Local-only activity and settings destination; no account system is implied. */
public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.location_settings_row).setOnClickListener(v ->
                openRecovery(RecoveryStateFragment.STATE_LOCATION));
        view.findViewById(R.id.sensor_settings_row).setOnClickListener(v ->
                openRecovery(RecoveryStateFragment.STATE_GPS));
        view.findViewById(R.id.clear_history_row).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_clear)
                        .setMessage(R.string.exploration_stop_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.done, null)
                        .show());
    }

    private void openRecovery(String state) {
        Bundle args = new Bundle();
        args.putString(RecoveryStateFragment.ARG_STATE, state);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_profile_to_recovery, args);
    }
}
