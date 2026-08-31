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

/** Explains foreground-location value before the later permission implementation requests it. */
public class LocationRationaleFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_location_rationale, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View.OnClickListener continueToExplore = v -> {
            requireContext().getSharedPreferences(OnboardingFragment.PREFERENCES,
                    Context.MODE_PRIVATE).edit()
                    .putBoolean(OnboardingFragment.KEY_COMPLETE, true)
                    .apply();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_rationale_to_explore);
        };
        view.findViewById(R.id.use_location_button).setOnClickListener(continueToExplore);
        view.findViewById(R.id.rationale_not_now_button).setOnClickListener(continueToExplore);
    }
}
