package com.comp90018.storystep.ui.profile;

import com.comp90018.storystep.platform.sensor.AndroidCapabilityReader;
import com.comp90018.storystep.ui.ViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
import android.widget.TextView;

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
    private ProfileViewModel viewModel;
    private AndroidCapabilityReader capabilities;


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
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_sensor).setMessage(R.string.profile_sensor_help)
                        .setPositiveButton(android.R.string.ok, null).show());
        capabilities = new AndroidCapabilityReader(requireContext());
        viewModel = new ViewModelProvider(this,
                ViewModelFactory.from(this)).get(ProfileViewModel.class);
        viewModel.getJourneys().observe(getViewLifecycleOwner(), count ->
                showCount(view, R.id.profile_journeys_count, count));
        viewModel.getVisits().observe(getViewLifecycleOwner(), count ->
                showCount(view, R.id.profile_visits_count, count));
        viewModel.getSaves().observe(getViewLifecycleOwner(), count ->
                showCount(view, R.id.profile_saves_count, count));
        View.OnClickListener openPrivacyData = v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_profile_to_privacy_data);
        view.findViewById(R.id.privacy_data_row).setOnClickListener(openPrivacyData);
        view.findViewById(R.id.clear_history_row).setOnClickListener(openPrivacyData);
    }

    @Override public void onResume() {
        super.onResume();
        viewModel.refresh();
        View view = requireView();
        String permission = getString(capabilities.preciseLocationGranted() ? R.string.profile_precise
                : capabilities.approximateLocationGranted() ? R.string.profile_approximate
                : R.string.profile_location_denied);
        ((TextView) view.findViewById(R.id.profile_location_status)).setText(
                getString(R.string.profile_location_status, permission,
                        getString(capabilities.locationEnabled() ? R.string.profile_location_on
                                : R.string.profile_location_off)));
        ((TextView) view.findViewById(R.id.profile_sensor_status)).setText(
                getString(R.string.profile_sensor_status,
                        getString(capabilities.accelerometerAvailable() ? R.string.profile_available
                                : R.string.profile_unavailable),
                        getString(capabilities.magnetometerAvailable() ? R.string.profile_available
                                : R.string.profile_unavailable)));
    }

    private void showCount(View view, int id, Integer count) {
        ((TextView) view.findViewById(id)).setText(count == null
                ? getString(R.string.profile_count_unknown)
                : java.text.NumberFormat.getIntegerInstance().format(count));
    }

    private void openRecovery(String state) {
        Bundle args = new Bundle();
        args.putString(RecoveryStateFragment.ARG_STATE, state);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_profile_to_recovery, args);
    }
}
