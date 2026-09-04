package com.comp90018.storystep.ui.story;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.ui.AttractionArtwork;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;

/**
 * Renders the story revealed for one attraction.
 *
 * <p>The text comes from the recorded visit or the attraction cache, never from copy written into
 * this screen: a story hard-coded here would keep claiming things about a place regardless of what
 * the data actually says.
 */
public class StoryFragment extends Fragment {

    private StoryViewModel viewModel;

    private TextView placeNameView;
    private TextView storyView;
    private ImageView heroView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_story, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        placeNameView = view.findViewById(R.id.story_place_name);
        storyView = view.findViewById(R.id.story_body);
        heroView = view.findViewById(R.id.story_hero);

        view.findViewById(R.id.story_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.add_memory_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_story_to_memory));

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(StoryViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);

        Bundle arguments = getArguments();
        String attractionId = arguments == null
                ? null
                : arguments.getString(NearbyAttractionsFragment.ARG_ATTRACTION_ID);
        viewModel.loadIfNeeded(attractionId);
    }

    private void render(StoryUiState state) {
        if (state.isLoading()) {
            placeNameView.setText(R.string.story_toolbar_title);
            storyView.setText(R.string.story_loading);
            return;
        }
        placeNameView.setText(state.hasPlaceName()
                ? state.getPlaceName()
                : getString(R.string.story_toolbar_title));
        // No text is stated as no text. Filling the gap with something plausible is exactly the
        // invented history this screen must not produce.
        storyView.setText(state.hasStory()
                ? state.getStory()
                : getString(R.string.story_no_content));
        if (state.getAttractionId() != null) {
            heroView.setImageResource(AttractionArtwork.heroDrawableFor(state.getAttractionId()));
        }
    }
}
