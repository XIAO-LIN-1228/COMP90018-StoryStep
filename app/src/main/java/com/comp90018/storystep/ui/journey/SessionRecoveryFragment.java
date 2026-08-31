package com.comp90018.storystep.ui.journey;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Recovery presentation for a locally checkpointed, non-collecting exploration session.
 *
 * <p>The current repository has no durable session implementation, so the displayed checkpoint is
 * deterministic prototype data. Actions are deliberately isolated here so REQ-15 can replace
 * them with repository commands without changing the layout or navigation contract.
 */
public final class SessionRecoveryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session_recovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.session_recovery_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.session_recovery_resume).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_session_recovery_to_exploration));
        view.findViewById(R.id.session_recovery_finish).setOnClickListener(v ->
                confirmFinish());
        view.findViewById(R.id.session_recovery_discard).setOnClickListener(v ->
                confirmDiscard());
    }

    private void confirmFinish() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.session_recovery_finish_title)
                .setMessage(R.string.session_recovery_finish_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.session_recovery_finish_action, (dialog, which) -> {
                    showPrototypeNotice();
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_session_recovery_to_summary);
                })
                .show();
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.session_recovery_discard_title)
                .setMessage(R.string.session_recovery_discard_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.session_recovery_discard_action, (dialog, which) -> {
                    showPrototypeNotice();
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_session_recovery_to_nearby);
                })
                .show();
    }

    private void showPrototypeNotice() {
        Toast.makeText(requireContext(), R.string.prototype_data_unchanged, Toast.LENGTH_SHORT)
                .show();
    }
}
