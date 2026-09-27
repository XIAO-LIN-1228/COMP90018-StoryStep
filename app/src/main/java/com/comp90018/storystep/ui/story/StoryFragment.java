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
import com.comp90018.storystep.platform.speech.AndroidTextToSpeechEngine;
import com.comp90018.storystep.ui.AttractionArtwork;
import com.comp90018.storystep.ui.TouchTargets;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.journey.JourneysFragment;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

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
    private TextView attributionView;
    private String themeSourceUrl;
    private View voiceControls;
    private MaterialButton voiceToggle;
    private TextView voiceStatus;
    private StorySpeechController speechController;
    private boolean hasSpeakableStory;

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
        attributionView = view.findViewById(R.id.story_attribution);
        voiceControls = view.findViewById(R.id.story_voice_controls);
        voiceToggle = view.findViewById(R.id.story_voice_toggle);
        voiceStatus = view.findViewById(R.id.story_voice_status);

        speechController = new StorySpeechController(
                new AndroidTextToSpeechEngine(requireContext()),
                new StorySpeechController.Listener() {
                    @Override
                    public void onStateChanged(StorySpeechController.State state, float rate) {
                        renderSpeechState(state);
                    }

                    @Override
                    public void onSpeechError() {
                        if (isAdded() && getView() != null) {
                            Snackbar.make(requireView(), R.string.story_voice_error,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                    }
                });
        voiceToggle.setOnClickListener(v -> speechController.togglePlayback());
        Chip slow = view.findViewById(R.id.story_voice_speed_slow);
        Chip normal = view.findViewById(R.id.story_voice_speed_normal);
        Chip fast = view.findViewById(R.id.story_voice_speed_fast);
        slow.setOnClickListener(v -> speechController.setRate(0.75f));
        normal.setOnClickListener(v -> speechController.setRate(1.0f));
        fast.setOnClickListener(v -> speechController.setRate(1.25f));
        normal.setChecked(true);
        renderSpeechState(speechController.getState());

        TouchTargets.expand(view.findViewById(R.id.story_back));
        view.findViewById(R.id.story_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(StoryViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);

        Bundle arguments = getArguments();
        String attractionId = arguments == null
                ? null
                : arguments.getString(NearbyAttractionsFragment.ARG_ATTRACTION_ID);
        // Present only when the story was reopened from history; null means the running session.
        String sessionId = arguments == null
                ? null
                : arguments.getString(JourneysFragment.ARG_SESSION_ID);
        viewModel.loadIfNeeded(attractionId, sessionId);
        com.comp90018.storystep.ui.theme.ThemeViewModel themeModel =
                new ViewModelProvider(this, ViewModelFactory.from(this))
                        .get(com.comp90018.storystep.ui.theme.ThemeViewModel.class);
        themeModel.showSession(sessionId);
        view.findViewById(R.id.story_theme).setOnClickListener(v -> {
            Bundle args = new Bundle();
            if (sessionId != null) { args.putString(com.comp90018.storystep.ui.theme.ThemeFragment.ARG_SESSION_ID, sessionId); }
            NavHostFragment.findNavController(this).navigate(R.id.themeFragment, args);
        });
        themeModel.getState().observe(getViewLifecycleOwner(), state -> {
            view.findViewById(R.id.story_theme).setVisibility(state.progress != null ? View.VISIBLE : View.GONE);
            themeSourceUrl = null;
            if (state.progress != null && attractionId != null) {
                for (com.comp90018.storystep.domain.theme.ThemeStop stop : state.progress.theme.stops) {
                    if (stop.id.equals(attractionId)) {
                        themeSourceUrl = stop.sourceUrl;
                    }
                }
            }
            StoryUiState current = viewModel.getUiState().getValue();
            if (current != null) { render(current); }
        });
    }

    private void render(StoryUiState state) {
        attributionView.setVisibility(state.isLoading() ? View.GONE : View.VISIBLE);
        hasSpeakableStory = !state.isLoading() && state.hasStory();
        voiceControls.setVisibility(hasSpeakableStory ? View.VISIBLE : View.GONE);
        speechController.setStory(hasSpeakableStory ? state.getStory() : null);
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
        int credit = state.getProvenance() == null ? R.string.story_source_unrecorded
                : com.comp90018.storystep.ui.AttributionText.attributionFor(state.getProvenance());
        String source = themeSourceUrl == null ? getString(credit) : getString(R.string.theme_source, themeSourceUrl);
        attributionView.setText(state.isArchived()
                ? getString(R.string.story_snapshot_credit, source, getString(R.string.story_saved_snapshot))
                : source);
        if (state.getAttractionId() != null) {
            heroView.setImageResource(AttractionArtwork.heroDrawableFor(state.getAttractionId()));
        }
    }

    private void renderSpeechState(StorySpeechController.State state) {
        if (voiceToggle == null) {
            return;
        }
        int toggleLabel;
        int statusLabel;
        boolean enabled = hasSpeakableStory;
        switch (state) {
            case INITIALIZING:
                toggleLabel = R.string.story_voice_listen;
                statusLabel = R.string.story_voice_preparing;
                enabled = false;
                break;
            case PLAYING:
                toggleLabel = R.string.story_voice_pause;
                statusLabel = R.string.story_voice_playing;
                break;
            case PAUSED:
                toggleLabel = R.string.story_voice_continue;
                statusLabel = R.string.story_voice_paused;
                break;
            case UNAVAILABLE:
                toggleLabel = R.string.story_voice_listen;
                statusLabel = R.string.story_voice_unavailable;
                enabled = false;
                break;
            case READY:
            default:
                toggleLabel = R.string.story_voice_listen;
                statusLabel = R.string.story_voice_ready;
                break;
        }
        voiceToggle.setText(toggleLabel);
        voiceToggle.setEnabled(enabled);
        voiceStatus.setText(statusLabel);
    }

    @Override
    public void onStop() {
        if (speechController != null) {
            speechController.pause();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (speechController != null) {
            speechController.shutdown();
            speechController = null;
        }
        voiceToggle = null;
        voiceStatus = null;
        voiceControls = null;
        super.onDestroyView();
    }
}
