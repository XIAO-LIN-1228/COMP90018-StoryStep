package com.comp90018.storystep.ui.journey;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.widget.StoryMapView;

/** Read-only summary of the prototype exploration session. */
public class JourneySummaryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_summary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        StoryMapView map = view.findViewById(R.id.summary_map);
        map.setExplorationMode(true);
        map.setCompactMode(true);

        view.findViewById(R.id.view_journey_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_summary_to_journey_detail));
        view.findViewById(R.id.summary_done_button).setOnClickListener(v -> {
            NavController controller = NavHostFragment.findNavController(this);
            if (!controller.popBackStack(R.id.nearbyAttractionsFragment, false)) {
                controller.navigate(R.id.nearbyAttractionsFragment);
            }
        });
    }
}
