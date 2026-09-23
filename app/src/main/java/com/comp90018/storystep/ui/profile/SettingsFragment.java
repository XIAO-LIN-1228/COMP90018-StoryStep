package com.comp90018.storystep.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.platform.sensor.AndroidCapabilityReader;

/** Device, preferences, data and the app: everything on the profile that is not the walker. */
public class SettingsFragment extends Fragment {

    private AndroidCapabilityReader capabilities;

    private final androidx.activity.result.ActivityResultLauncher<
            androidx.activity.result.PickVisualMediaRequest> pickAvatar =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }
                        android.content.Context app = requireContext().getApplicationContext();
                        com.comp90018.storystep.di.AppContainer container =
                                ((com.comp90018.storystep.StoryStepApplication)
                                        requireActivity().getApplication()).getAppContainer();
                        container.getIoExecutor().execute(() -> {
                            com.comp90018.storystep.platform.media.ProfileAvatar.set(app, uri);
                            container.getMainExecutor().execute(this::showAvatar);
                        });
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        capabilities = new AndroidCapabilityReader(requireContext());
        view.findViewById(R.id.settings_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.avatar_row).setOnClickListener(v -> {
            if (!com.comp90018.storystep.platform.media.ProfileAvatar.exists(requireContext())) {
                choosePhoto();
                return;
            }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_avatar)
                    .setItems(new CharSequence[]{
                            getString(R.string.settings_avatar_change),
                            getString(R.string.settings_avatar_remove)}, (dialog, which) -> {
                        if (which == 0) {
                            choosePhoto();
                        } else {
                            com.comp90018.storystep.platform.media.ProfileAvatar
                                    .clear(requireContext());
                            showAvatar();
                        }
                    })
                    .show();
        });
        showAvatar();
        view.findViewById(R.id.location_settings_row).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(RecoveryStateFragment.ARG_STATE, RecoveryStateFragment.STATE_LOCATION);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_settings_to_recovery, args);
        });
        view.findViewById(R.id.sensor_settings_row).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_sensor).setMessage(R.string.profile_sensor_help)
                        .setPositiveButton(android.R.string.ok, null).show());
        View.OnClickListener openPrivacyData = v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_settings_to_privacy_data);
        view.findViewById(R.id.privacy_data_row).setOnClickListener(openPrivacyData);
        view.findViewById(R.id.clear_history_row).setOnClickListener(openPrivacyData);
    }

    private void choosePhoto() {
        pickAvatar.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(androidx.activity.result.contract.ActivityResultContracts
                        .PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void showAvatar() {
        View view = getView();
        if (view == null) {
            return;
        }
        com.comp90018.storystep.platform.media.ProfileAvatar.bind(
                view.findViewById(R.id.settings_avatar));
        ((TextView) view.findViewById(R.id.settings_avatar_hint)).setText(
                com.comp90018.storystep.platform.media.ProfileAvatar.exists(requireContext())
                        ? R.string.settings_avatar_set : R.string.settings_avatar_choose);
    }

    @Override
    public void onResume() {
        super.onResume();
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
}
