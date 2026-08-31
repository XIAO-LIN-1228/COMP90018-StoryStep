package com.comp90018.storystep.ui.journey;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Recovery presentation for a non-collecting exploration session.
 *
 * <p>Actions go through the process-wide coordinator via {@link SessionRecoveryViewModel}. The
 * current store is process-lifetime only, so recovery after process death remains REQ-15 work, but
 * this screen never invents a second session or changes state only in its own view hierarchy.
 */
public final class SessionRecoveryFragment extends Fragment {

    private enum PendingAction {
        NONE,
        RESUME,
        FINISH,
        DISCARD
    }

    private SessionRecoveryViewModel viewModel;
    private MaterialButton resumeButton;
    private MaterialButton finishButton;
    private MaterialButton discardButton;
    private TextView pausedLabel;
    private TextView checkpoint;
    private PendingAction pendingAction = PendingAction.NONE;

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
        resumeButton = view.findViewById(R.id.session_recovery_resume);
        finishButton = view.findViewById(R.id.session_recovery_finish);
        discardButton = view.findViewById(R.id.session_recovery_discard);
        pausedLabel = view.findViewById(R.id.session_recovery_paused_label);
        checkpoint = view.findViewById(R.id.session_recovery_checkpoint);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(SessionRecoveryViewModel.class);
        viewModel.getSession().observe(getViewLifecycleOwner(), this::renderSession);

        view.findViewById(R.id.session_recovery_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        resumeButton.setOnClickListener(v -> {
            pendingAction = PendingAction.RESUME;
            setActionsEnabled(false, false);
            if (!viewModel.onResumeRequested()) {
                pendingAction = PendingAction.NONE;
                SessionSummary current = viewModel.getSession().getValue();
                if (current != null) {
                    renderSession(current);
                }
                Toast.makeText(requireContext(), R.string.session_recovery_resume_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        });
        finishButton.setOnClickListener(v -> confirmFinish());
        discardButton.setOnClickListener(v -> confirmDiscard());
    }

    private void renderSession(SessionSummary summary) {
        long activeMinutes = summary.getActiveDurationMillis() / 60_000L;
        checkpoint.setText(getString(
                R.string.session_recovery_checkpoint_format,
                activeMinutes,
                summary.getSegment()));

        SessionState state = summary.getState();
        if (state == SessionState.PAUSED) {
            pausedLabel.setText(pausedLabel(summary.getPauseReason()));
        }

        boolean canResume = state == SessionState.PAUSED
                && summary.getPauseReason() == PauseReason.APP_NOT_VISIBLE;
        boolean canEnd = state == SessionState.ACTIVE || state == SessionState.PAUSED;
        setActionsEnabled(canResume, canEnd);

        if (pendingAction == PendingAction.RESUME && state == SessionState.ACTIVE) {
            pendingAction = PendingAction.NONE;
            navigate(R.id.action_session_recovery_to_exploration);
        } else if (pendingAction == PendingAction.FINISH && state == SessionState.COMPLETED) {
            pendingAction = PendingAction.NONE;
            navigate(R.id.action_session_recovery_to_summary);
        } else if (pendingAction == PendingAction.DISCARD && state == SessionState.IDLE) {
            pendingAction = PendingAction.NONE;
            navigate(R.id.action_session_recovery_to_nearby);
        }
    }

    private int pausedLabel(@Nullable PauseReason reason) {
        if (reason == PauseReason.PERMISSION_LOST) {
            return R.string.session_recovery_paused_permission;
        }
        if (reason == PauseReason.PROVIDER_LOST) {
            return R.string.session_recovery_paused_provider;
        }
        return R.string.session_recovery_paused;
    }

    private void setActionsEnabled(boolean resumeEnabled, boolean endEnabled) {
        resumeButton.setEnabled(resumeEnabled);
        finishButton.setEnabled(endEnabled);
        discardButton.setEnabled(endEnabled);
    }

    private void confirmFinish() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.session_recovery_finish_title)
                .setMessage(R.string.session_recovery_finish_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.session_recovery_finish_action, (dialog, which) -> {
                    pendingAction = PendingAction.FINISH;
                    setActionsEnabled(false, false);
                    viewModel.onFinishRequested();
                })
                .show();
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.session_recovery_discard_title)
                .setMessage(R.string.session_recovery_discard_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.session_recovery_discard_action, (dialog, which) -> {
                    pendingAction = PendingAction.DISCARD;
                    setActionsEnabled(false, false);
                    viewModel.onDiscardRequested();
                })
                .show();
    }

    private void navigate(int actionId) {
        NavController controller = NavHostFragment.findNavController(this);
        if (controller.getCurrentDestination() == null
                || controller.getCurrentDestination().getId() != R.id.sessionRecoveryFragment) {
            Toast.makeText(requireContext(), R.string.session_recovery_action_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        controller.navigate(actionId);
    }
}
