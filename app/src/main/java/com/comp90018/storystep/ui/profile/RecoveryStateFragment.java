package com.comp90018.storystep.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.google.android.material.button.MaterialButton;

/** Reusable recovery page for permission, GPS and offline UI states. */
public class RecoveryStateFragment extends Fragment {

    public static final String ARG_STATE = "state";
    public static final String STATE_LOCATION = "location";
    public static final String STATE_GPS = "gps";
    public static final String STATE_OFFLINE = "offline";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recovery_state, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView title = view.findViewById(R.id.recovery_title);
        TextView body = view.findViewById(R.id.recovery_body);
        MaterialButton primary = view.findViewById(R.id.recovery_primary);
        MaterialButton secondary = view.findViewById(R.id.recovery_secondary);
        String state = getArguments() == null ? STATE_LOCATION
                : getArguments().getString(ARG_STATE, STATE_LOCATION);

        view.findViewById(R.id.recovery_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        if (STATE_GPS.equals(state)) {
            title.setText(R.string.location_services_title);
            body.setText(R.string.location_services_body);
            primary.setText(R.string.location_turn_on_gps);
            secondary.setText(R.string.not_now);
            primary.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        } else if (STATE_OFFLINE.equals(state)) {
            title.setText(R.string.offline_title);
            body.setText(R.string.offline_body);
            primary.setText(R.string.offline_saved);
            secondary.setText(R.string.offline_retry);
            primary.setOnClickListener(v -> NavHostFragment.findNavController(this)
                    .navigate(R.id.savedFragment));
        } else {
            title.setText(R.string.location_access_title);
            body.setText(R.string.location_access_body);
            primary.setText(R.string.location_open_settings);
            secondary.setText(R.string.location_browse);
            primary.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            });
        }
        secondary.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
    }
}
