package com.comp90018.storystep.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.opening.OpeningHoursEvaluator;
import com.comp90018.storystep.domain.opening.OpeningStatus;
import com.comp90018.storystep.ui.AttributionText;
import com.comp90018.storystep.ui.CategoryLabels;
import com.comp90018.storystep.ui.AttractionArtwork;
import com.comp90018.storystep.ui.TouchTargets;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.comp90018.storystep.ui.nearby.NearbyAttractionsFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.ZonedDateTime;

/**
 * Details of one attraction. The identifier arrives as a plain Bundle argument; Safe Args is
 * intentionally not used, because it would require the Kotlin Gradle plugin.
 */
public class AttractionDetailFragment extends Fragment {

    private AttractionDetailViewModel viewModel;

    private TextView nameView;
    private TextView categoryView;
    private TextView descriptionView;
    // Indoor/outdoor reads as a filled pill beside the category rather than as a
    // Material chip competing with the name; a TextView carries the same text API.
    private TextView venueTypeChip;
    private TextView attributionView;
    private ImageView imageView;
    private TextView photoCredit;
    private ImageButton saveButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attraction_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameView = view.findViewById(R.id.detail_name);
        categoryView = view.findViewById(R.id.detail_category);
        descriptionView = view.findViewById(R.id.detail_description);
        venueTypeChip = view.findViewById(R.id.detail_venue_type);
        attributionView = view.findViewById(R.id.detail_attribution);
        imageView = view.findViewById(R.id.detail_image);
        photoCredit = view.findViewById(R.id.detail_photo_credit);
        saveButton = view.findViewById(R.id.detail_save_button);

        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(AttractionDetailViewModel.class);

        TouchTargets.expand(view.findViewById(R.id.detail_back_button));
        view.findViewById(R.id.detail_back_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        saveButton.setOnClickListener(v -> viewModel.onSaveToggleRequested());
        // The place goes into the walk being planned rather than starting one of its own: the
        // Start tab is where a walk begins, and one place is rarely the whole of it.
        view.findViewById(R.id.detail_start_button).setOnClickListener(v -> addToWalk());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSaveFailed().observe(getViewLifecycleOwner(), this::reportSaveFailure);

        Bundle arguments = getArguments();
        String attractionId = arguments == null
                ? null
                : arguments.getString(NearbyAttractionsFragment.ARG_ATTRACTION_ID);
        viewModel.loadIfNeeded(attractionId);
    }

    private void render(AttractionDetailUiState state) {
        Attraction attraction = state.getAttraction();
        renderSaveButton(state);
        if (state.getStatus() != AttractionDetailUiState.Status.CONTENT || attraction == null) {
            boolean failed = state.getStatus() == AttractionDetailUiState.Status.ERROR;
            nameView.setText(failed ? getString(R.string.detail_error) : "");
            categoryView.setText("");
            descriptionView.setText("");
            venueTypeChip.setVisibility(View.GONE);
            attributionView.setText("");
            return;
        }
        nameView.setText(attraction.getName());
        categoryView.setText(CategoryLabels.labelOf(attraction.getCategory()));
        // A fetched place usually has no description in OpenStreetMap. Saying so is the honest
        // rendering; leaving the field blank reads as a loading failure, and writing a sentence
        // here would be this app inventing a fact about a real place.
        if (attraction.hasDescription()) {
            descriptionView.setText(attraction.getDescription());
        } else {
            descriptionView.setText(R.string.attraction_no_description);
        }
        bindFacts(attraction.getFacts());
        String attribution = getString(AttributionText.attributionFor(attraction.getProvenance()));
        // A saved copy is shown when the place is no longer in the cache. The line says which one
        // the user is reading: the snapshot was taken when they saved it, and the real record may
        // have moved on since.
        attributionView.setText(state.isFromSavedSnapshot()
                ? getString(R.string.detail_saved_snapshot, attribution)
                : attribution);
        // Only three photographs ship with the app, and a fetched place almost never has
        // one. A 248dp placeholder for "no photo" is dead space on most real screens, so the
        // hero simply collapses; the pills and prose above and below it already carry the page.
        int hero = AttractionArtwork.heroDrawableFor(attraction.getId());
        int heroHeight = getResources().getDimensionPixelSize(R.dimen.hero_photo_height);
        if (attraction.getImageUrl() != null) {
            imageView.getLayoutParams().height = heroHeight;
            imageView.requestLayout();
            imageView.setVisibility(View.VISIBLE);
            photoCredit.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(imageView)
                    .load(attraction.getImageUrl())
                    .placeholder(R.drawable.bg_photo_placeholder)
                    .error(R.drawable.bg_photo_placeholder)
                    .centerCrop()
                    .into(imageView);
        } else if (hero == R.drawable.bg_photo_placeholder) {
            // No photograph at all: the hero collapses to a short band that still carries the
            // back control, and the page opens on the name.
            imageView.setVisibility(View.INVISIBLE);
            imageView.getLayoutParams().height = getResources()
                    .getDimensionPixelSize(R.dimen.hero_collapsed_height);
            imageView.requestLayout();
            photoCredit.setVisibility(View.GONE);
        } else {
            imageView.getLayoutParams().height = heroHeight;
            imageView.requestLayout();
            imageView.setVisibility(View.VISIBLE);
            photoCredit.setVisibility(View.GONE);
            imageView.setImageResource(hero);
        }
        venueTypeChip.setVisibility(View.VISIBLE);
        venueTypeChip.setText(venueTypeLabel(attraction.getIndoor()));
    }

    /**
     * The chip label, including the case where nothing is known.
     *
     * <p>An unknown classification gets its own label rather than borrowing "Outdoor". A place the
     * provider never described is not a place that was described as outdoors, and the chip is read
     * as a statement about the place.
     */
    private static int venueTypeLabel(IndoorClassification classification) {
        switch (classification) {
            case INDOOR:
                return R.string.attraction_indoor;
            case OUTDOOR:
                return R.string.attraction_outdoor;
            default:
                return R.string.attraction_indoor_unknown;
        }
    }

    /**
     * The bookmark is hidden until there is something to save, so it cannot be pressed against an
     * attraction that failed to load. Its label changes with its state — an icon alone tells a
     * screen reader nothing about which way pressing it goes.
     */
    /**
     * The practical facts, one row each, in the order a visitor asks them: when is it open,
     * can I get in, what does it cost, how do I reach it. The whole block goes when the
     * source recorded none of them, which is most places.
     */
    private void bindFacts(com.comp90018.storystep.domain.model.PlaceFacts facts) {
        View heading = requireView().findViewById(R.id.detail_facts_heading);
        android.widget.LinearLayout rows = requireView().findViewById(R.id.detail_facts);
        rows.removeAllViews();

        ZonedDateTime now = ZonedDateTime.now();
        OpeningStatus openingStatus = OpeningHoursEvaluator.evaluate(facts.getOpeningHours(),
                now.getDayOfWeek(), now.getHour() * 60 + now.getMinute());
        addFact(rows, R.drawable.ic_clock, R.string.detail_hours_label,
                OpeningHoursText.formatWithStatus(facts.getOpeningHours(), openingStatus,
                        getString(R.string.detail_open_now),
                        getString(R.string.detail_closed_now),
                        getString(R.string.detail_hours_uncertain)), null);
        Integer access = OpeningHoursText.accessLabel(facts.getWheelchair());
        addFact(rows, R.drawable.ic_access, R.string.detail_access_label,
                access == null ? null : getString(access), null);
        addFact(rows, R.drawable.ic_ticket, R.string.detail_fee_label,
                OpeningHoursText.feeLabel(facts.getFee(), getString(R.string.detail_fee_free),
                        getString(R.string.detail_fee_charged)), null);
        addFact(rows, R.drawable.ic_phone, R.string.detail_phone_label, facts.getPhone(),
                facts.getPhone() == null ? null : android.net.Uri.parse(
                        "tel:" + facts.getPhone().replaceAll("[^+0-9]", "")));
        addFact(rows, R.drawable.ic_link, R.string.detail_website_label, facts.getWebsite(),
                facts.getWebsite() == null ? null : android.net.Uri.parse(facts.getWebsite()));

        boolean any = rows.getChildCount() > 0;
        heading.setVisibility(any ? View.VISIBLE : View.GONE);
        rows.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** Adds one row, or nothing when the fact was not recorded. A link row opens it outside. */
    private void addFact(android.widget.LinearLayout rows, int icon, int label, String value,
                         android.net.Uri open) {
        if (value == null || value.isEmpty()) {
            return;
        }
        View row = getLayoutInflater().inflate(R.layout.view_place_fact, rows, false);
        ((android.widget.ImageView) row.findViewById(R.id.fact_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.fact_label)).setText(label);
        TextView text = row.findViewById(R.id.fact_value);
        text.setText(value);
        if (open != null) {
            text.setTextColor(androidx.core.content.ContextCompat.getColor(
                    requireContext(), R.color.story_signal_ink));
            android.util.TypedValue ripple = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);
            row.setOnClickListener(v -> {
                try {
                    startActivity(new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, open));
                } catch (android.content.ActivityNotFoundException nothingHandlesIt) {
                    // Nothing on the phone opens it; the number or address is still readable.
                }
            });
        }
        rows.addView(row);
    }

    private void renderSaveButton(AttractionDetailUiState state) {
        renderPlanButton();
        boolean loaded = state.getStatus() == AttractionDetailUiState.Status.CONTENT
                && state.getAttraction() != null;
        saveButton.setVisibility(loaded ? View.VISIBLE : View.GONE);
        saveButton.setImageResource(state.isSaved()
                ? R.drawable.ic_bookmark_filled
                : R.drawable.ic_bookmark_outline);
        saveButton.setContentDescription(getString(state.isSaved()
                ? R.string.detail_unsave
                : R.string.detail_save));
    }

    private void reportSaveFailure(Boolean failed) {
        if (!Boolean.TRUE.equals(failed)) {
            return;
        }
        Toast.makeText(requireContext(), R.string.detail_save_failed, Toast.LENGTH_SHORT).show();
        viewModel.consumeSaveFailure();
    }

    /**
     * Puts the place into the walk being planned, and says so. Already in the walk, or the walk
     * already full, are both ordinary answers rather than failures.
     */
    private void addToWalk() {
        AttractionDetailUiState state = viewModel.getUiState().getValue();
        Attraction attraction = state == null ? null : state.getAttraction();
        if (attraction == null) {
            return;
        }
        com.comp90018.storystep.application.WalkPlanner planner =
                ((com.comp90018.storystep.StoryStepApplication) requireActivity().getApplication())
                        .getAppContainer().getWalkPlanner();
        com.comp90018.storystep.domain.plan.WalkPlan before = planner.plan();
        if (before.contains(attraction.getId())) {
            Toast.makeText(requireContext(), R.string.detail_in_walk, Toast.LENGTH_SHORT).show();
            return;
        }
        if (before.isFull()) {
            Toast.makeText(requireContext(), getString(R.string.plan_full,
                    com.comp90018.storystep.domain.plan.WalkPlan.MAX_STOPS),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        planner.add(attraction);
        Toast.makeText(requireContext(), R.string.plan_added, Toast.LENGTH_SHORT).show();
        renderPlanButton();
    }

    /** The action says whether the place is already in the walk. */
    private void renderPlanButton() {
        AttractionDetailUiState state = viewModel.getUiState().getValue();
        Attraction attraction = state == null ? null : state.getAttraction();
        com.google.android.material.button.MaterialButton button =
                requireView().findViewById(R.id.detail_start_button);
        boolean inWalk = attraction != null
                && ((com.comp90018.storystep.StoryStepApplication) requireActivity().getApplication())
                        .getAppContainer().getWalkPlanner().plan().contains(attraction.getId());
        button.setText(inWalk ? R.string.detail_in_walk : R.string.detail_add_to_walk);
        button.setEnabled(!inWalk);
    }

    private void showStartConfirmation() {
        AttractionDetailUiState state = viewModel.getUiState().getValue();
        Attraction attraction = state == null ? null : state.getAttraction();
        if (attraction == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_title)
                .setMessage(getString(R.string.confirm_destination, attraction.getName()) + "\n\n"
                        + getString(R.string.confirm_location) + "\n"
                        + getString(R.string.confirm_stop))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm_start, (dialog, which) -> startExploring())
                .show();
    }

    /**
     * Opens the dashboard only once a session is genuinely running. Without location access the
     * destination is remembered and the user is returned to the nearby screen, which owns the
     * permission flow; navigating to a dashboard with no session would show an explore screen that
     * is not exploring.
     */
    private void startExploring() {
        if (viewModel.onStartExplorationRequested()) {
            NavHostFragment.findNavController(this).navigate(R.id.action_detail_to_exploration);
            return;
        }
        Toast.makeText(requireContext(), R.string.start_needs_location, Toast.LENGTH_LONG).show();
        NavHostFragment.findNavController(this)
                .popBackStack(R.id.nearbyAttractionsFragment, false);
    }
}
