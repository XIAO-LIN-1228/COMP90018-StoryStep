package com.comp90018.storystep.ui.profile;

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
        view.findViewById(R.id.profile_settings).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_settings));
        viewModel = new ViewModelProvider(this,
                ViewModelFactory.from(this)).get(ProfileViewModel.class);
        viewModel.getJourneys().observe(getViewLifecycleOwner(), count ->
                showCount(view, R.id.profile_journeys_count, count));
        viewModel.getVisits().observe(getViewLifecycleOwner(), count ->
                showCount(view, R.id.profile_visits_count, count));
        viewModel.getSaves().observe(getViewLifecycleOwner(), count ->
                showCount(view, R.id.profile_saves_count, count));
        viewModel.getDistanceMeters().observe(getViewLifecycleOwner(), metres ->
                ((TextView) view.findViewById(R.id.profile_distance_total)).setText(metres == null
                        ? getString(R.string.profile_count_unknown)
                        : com.comp90018.storystep.ui.DistanceText.format(getResources(), metres)));
        viewModel.getFirstWalkMillis().observe(getViewLifecycleOwner(), first ->
                ((TextView) view.findViewById(R.id.profile_subtitle)).setText(first == null
                        ? getString(R.string.profile_local)
                        : getString(R.string.profile_since, new java.text.SimpleDateFormat(
                                "MMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date(first)))));

        androidx.recyclerview.widget.RecyclerView walks = view.findViewById(R.id.profile_walks);
        View walksHeading = view.findViewById(R.id.profile_walks_heading);
        ProfileWalkAdapter walkAdapter = new ProfileWalkAdapter(walk -> {
            Bundle args = new Bundle();
            args.putString(com.comp90018.storystep.ui.journey.JourneysFragment.ARG_SESSION_ID,
                    walk.getId());
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_journey_detail, args);
        });
        walks.setAdapter(walkAdapter);
        viewModel.getRecentWalks().observe(getViewLifecycleOwner(), recent -> {
            boolean any = recent != null && !recent.isEmpty();
            walksHeading.setVisibility(any ? View.VISIBLE : View.GONE);
            walks.setVisibility(any ? View.VISIBLE : View.GONE);
            walkAdapter.submitList(any ? recent : java.util.Collections.emptyList());
        });

        androidx.recyclerview.widget.RecyclerView places = view.findViewById(R.id.profile_saved);
        View placesHeading = view.findViewById(R.id.profile_saved_heading);
        view.findViewById(R.id.profile_saved_all).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_saved));
        ProfilePlaceAdapter placeAdapter = new ProfilePlaceAdapter(place -> {
            Bundle args = new Bundle();
            args.putString(com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment
                    .ARG_ATTRACTION_ID, place.getId());
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_attraction_detail, args);
        });
        places.setAdapter(placeAdapter);
        viewModel.getSavedPlaces().observe(getViewLifecycleOwner(), kept -> {
            boolean any = kept != null && !kept.isEmpty();
            placesHeading.setVisibility(any ? View.VISIBLE : View.GONE);
            places.setVisibility(any ? View.VISIBLE : View.GONE);
            placeAdapter.submitList(any ? kept : java.util.Collections.emptyList());
        });
    }

    @Override public void onResume() {
        super.onResume();
        viewModel.refresh();
        com.comp90018.storystep.platform.media.ProfileAvatar.bind(
                requireView().findViewById(R.id.profile_avatar));
    }

    private void showCount(View view, int id, Integer count) {
        ((TextView) view.findViewById(id)).setText(count == null
                ? getString(R.string.profile_count_unknown)
                : java.text.NumberFormat.getIntegerInstance().format(count));
    }
}
