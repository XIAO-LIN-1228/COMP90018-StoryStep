package com.comp90018.storystep.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Local-only privacy and data controls. No account, cloud upload or public route is implied. */
public final class PrivacyDataFragment extends Fragment {

    private PrivacyDataViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_privacy_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(PrivacyDataViewModel.class);
        viewModel.getSavedCleared().observe(getViewLifecycleOwner(), this::reportSavedCleared);

        view.findViewById(R.id.privacy_data_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.privacy_manage_permissions).setOnClickListener(v ->
                openAppSettings());
        view.findViewById(R.id.privacy_clear_history).setOnClickListener(v ->
                confirmPrototypeClear(R.string.privacy_clear_history_title,
                        R.string.privacy_clear_history_body));
        view.findViewById(R.id.privacy_clear_saved).setOnClickListener(v ->
                confirmClearSaved());
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private void confirmClearSaved() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privacy_clear_saved_title)
                .setMessage(R.string.privacy_clear_saved_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.privacy_clear_action, (dialog, which) ->
                        viewModel.onClearSavedConfirmed())
                .show();
    }

    private void reportSavedCleared(PrivacyDataViewModel.ClearOutcome outcome) {
        if (outcome == null) {
            return;
        }
        Toast.makeText(requireContext(),
                outcome == PrivacyDataViewModel.ClearOutcome.CLEARED
                        ? R.string.saved_cleared
                        : R.string.saved_clear_failed,
                Toast.LENGTH_SHORT).show();
        viewModel.consumeSavedCleared();
    }

    /** Still a prototype: clearing journey history waits on REQ-16's deletion rules. */
    private void confirmPrototypeClear(int title, int message) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.privacy_clear_action, (dialog, which) ->
                        Toast.makeText(requireContext(), R.string.prototype_data_unchanged,
                                Toast.LENGTH_SHORT).show())
                .show();
    }
}
