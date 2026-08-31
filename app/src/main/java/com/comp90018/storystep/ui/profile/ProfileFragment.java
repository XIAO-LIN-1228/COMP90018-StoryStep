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
        View.OnClickListener openPrivacyData = v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_profile_to_privacy_data);
        view.findViewById(R.id.privacy_data_row).setOnClickListener(openPrivacyData);
        view.findViewById(R.id.clear_history_row).setOnClickListener(openPrivacyData);
    }

    private void openRecovery(String state) {
        Bundle args = new Bundle();
        args.putString(RecoveryStateFragment.ARG_STATE, state);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_profile_to_recovery, args);
    }
}
