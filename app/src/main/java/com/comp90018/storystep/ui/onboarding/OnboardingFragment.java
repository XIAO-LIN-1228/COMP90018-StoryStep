package com.comp90018.storystep.ui.onboarding;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;

/** First-run value proposition; returning users are routed directly to Explore. */
public class OnboardingFragment extends Fragment {

    static final String PREFERENCES = "story_step_preferences";
    static final String KEY_COMPLETE = "onboarding_complete";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean complete = requireContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETE, false);
        if (complete) {
            view.post(() -> NavHostFragment.findNavController(this)
                    .navigate(R.id.action_onboarding_to_explore));
            return;
        }
        view.findViewById(R.id.onboarding_start_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_onboarding_to_rationale));
    }
}
