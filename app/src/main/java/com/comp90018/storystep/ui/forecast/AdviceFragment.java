package com.comp90018.storystep.ui.forecast;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.DepartureAdvice;
import com.comp90018.storystep.domain.forecast.DepartureAdvisor;
import com.comp90018.storystep.domain.forecast.SevereWeather;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.ui.HistoryText;
import com.comp90018.storystep.ui.ViewModelFactory;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * When to leave on the planned walk (decision D-08): each departure over the next day, with the
 * chance of rain at some point during the whole walk. Opened from the plan's summary line.
 */
public class AdviceFragment extends Fragment {

    public static final String ARG_LATITUDE = "latitude";
    public static final String ARG_LONGITUDE = "longitude";
    public static final String ARG_WALKING_MILLIS = "walkingMillis";
    public static final String ARG_STOPS = "stops";

    private AdviceViewModel viewModel;
    private final DepartureRowAdapter adapter = new DepartureRowAdapter();

    private TextView duration;
    private View summary;
    private TextView best;
    private TextView note;
    private View loading;
    private View errorGroup;
    private TextView errorText;
    private RecyclerView list;
    private TextView source;
    private TextView zoneNote;
    /** The trip the too-long dialog was last shown for, so rotation does not show it again. */
    private long dialogShownFor = -1;

    /** Arguments for opening this page on a planned walk. */
    public static Bundle arguments(GeoPoint origin, long walkingMillis, int stops) {
        Bundle args = new Bundle();
        args.putDouble(ARG_LATITUDE, origin.getLatitude());
        args.putDouble(ARG_LONGITUDE, origin.getLongitude());
        args.putLong(ARG_WALKING_MILLIS, walkingMillis);
        args.putInt(ARG_STOPS, stops);
        return args;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_advice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            dialogShownFor = savedInstanceState.getLong("dialogShownFor", -1);
        }
        view.findViewById(R.id.advice_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        duration = view.findViewById(R.id.advice_duration);
        summary = view.findViewById(R.id.advice_summary);
        best = view.findViewById(R.id.advice_best);
        note = view.findViewById(R.id.advice_note);
        loading = view.findViewById(R.id.advice_loading);
        errorGroup = view.findViewById(R.id.advice_error_group);
        errorText = view.findViewById(R.id.advice_error);
        list = view.findViewById(R.id.advice_list);
        source = view.findViewById(R.id.advice_source);
        zoneNote = view.findViewById(R.id.advice_zone_note);
        list.setAdapter(adapter);

        Bundle args = requireArguments();
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(AdviceViewModel.class);
        viewModel.init(new GeoPoint(args.getDouble(ARG_LATITUDE), args.getDouble(ARG_LONGITUDE)),
                args.getLong(ARG_WALKING_MILLIS), args.getInt(ARG_STOPS));

        bindStayChips(view.findViewById(R.id.advice_stay_group));
        view.findViewById(R.id.advice_retry).setOnClickListener(v -> viewModel.retry());
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.onResume();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("dialogShownFor", dialogShownFor);
    }

    private void bindStayChips(ChipGroup group) {
        for (int minutes : AdviceViewModel.STAY_MINUTES) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_stay_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(getString(R.string.advice_stay_minutes, minutes));
            chip.setTag(minutes);
            group.addView(chip);
        }
        viewModel.getStayMinutes().observe(getViewLifecycleOwner(), minutes -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                Chip chip = (Chip) group.getChildAt(i);
                chip.setChecked(minutes.equals(chip.getTag()));
            }
        });
        group.setOnCheckedStateChangeListener((g, checked) -> {
            if (!checked.isEmpty()) {
                viewModel.onStayChosen((Integer) g.findViewById(checked.get(0)).getTag());
            }
        });
    }

    private void render(AdviceUiState state) {
        duration.setText(getString(R.string.advice_walk_duration,
                HistoryText.formatDuration(getResources(), state.getTripMillis())));
        AdviceUiState.Status status = state.getStatus();
        loading.setVisibility(status == AdviceUiState.Status.LOADING ? View.VISIBLE : View.GONE);
        boolean showError = status == AdviceUiState.Status.ERROR
                || status == AdviceUiState.Status.TOO_LONG;
        errorGroup.setVisibility(showError ? View.VISIBLE : View.GONE);
        boolean content = status == AdviceUiState.Status.CONTENT;
        list.setVisibility(content ? View.VISIBLE : View.GONE);
        summary.setVisibility(content ? View.VISIBLE : View.GONE);
        source.setVisibility(content ? View.VISIBLE : View.GONE);

        if (status == AdviceUiState.Status.ERROR) {
            errorText.setText(ForecastText.error(state.getReason()));
        } else if (status == AdviceUiState.Status.TOO_LONG) {
            errorText.setText(R.string.advice_too_long_inline);
            showTooLongDialog(state.getTripMillis());
        }
        // Unusable data was fetched fine; asking again within the cache lifetime returns the same.
        errorGroup.findViewById(R.id.advice_retry).setVisibility(
                status == AdviceUiState.Status.ERROR && state.getReason()
                        != com.comp90018.storystep.domain.repository.ForecastUnavailableException
                        .Reason.UNUSABLE ? View.VISIBLE : View.GONE);

        DepartureAdvice advice = state.getAdvice();
        String note = advice == null ? null : ForecastText.zoneNote(getResources(),
                state.getPlace(), System.currentTimeMillis(), java.util.TimeZone.getDefault());
        zoneNote.setVisibility(note == null ? View.GONE : View.VISIBLE);
        zoneNote.setText(note);
        if (advice == null) {
            adapter.submit(java.util.Collections.emptyList(), -1, state.getPlace(), null, 0L);
            return;
        }
        adapter.submit(advice.getCandidates(), advice.getBestIndex(), state.getPlace(),
                state.getHourly(), advice.getComputedAtMillis());
        renderSummary(advice, state.getPlace(), state.getHourlyState());
        source.setText(getString(R.string.advice_two_lines,
                getString(R.string.advice_source,
                        AdviceText.models(advice.getModels(), getString(R.string.advice_and))),
                getString(R.string.advice_alerts_disclaimer)));
        int bestIndex = Math.max(0, advice.getBestIndex());
        list.post(() -> {
            RecyclerView.LayoutManager manager = list.getLayoutManager();
            if (manager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) manager).scrollToPositionWithOffset(bestIndex, 0);
            }
        });
    }

    private void renderSummary(DepartureAdvice advice,
                               com.comp90018.storystep.domain.forecast.ForecastPlace place,
                               AdviceViewModel.HourlyState hourlyState) {
        String unchecked;
        if (hourlyState == AdviceViewModel.HourlyState.PENDING) {
            unchecked = getString(R.string.advice_severe_pending);
        } else {
            unchecked = advice.isSevereChecked() ? null
                    : getString(R.string.advice_severe_unchecked);
        }
        if (advice.isSevereAllDaytime()) {
            // Every daylight departure meets a severe threshold: name none, suggest another day.
            best.setText(R.string.advice_severe_all_title);
            showNote(getString(R.string.advice_severe_all_note), unchecked);
            return;
        }
        DepartureAdvice.Candidate chosen = advice.getBest();
        // Not while the check is loading: "leave whenever you like" must not precede a hazard.
        if (advice.isDryThroughout() && hourlyState != AdviceViewModel.HourlyState.PENDING) {
            // Whenever the walker leaves it stays dry: say so instead of naming a "best" time
            // that is no better than any other.
            best.setText(R.string.advice_dry_title);
            showNote(getString(R.string.advice_dry_note), unchecked);
            return;
        }
        String chance = AdviceText.percent(getResources(), chosen);
        long start = chosen.getStartMillis();
        if (chosen.isNow()) {
            best.setText(getString(R.string.advice_best_now, chance));
        } else if (ForecastText.dayOffset(advice.getComputedAtMillis(), start,
                ForecastText.zone(place)) == 1) {
            // A bare "7:00 AM" read in the evening looks like a time already past.
            best.setText(getString(R.string.advice_best_tomorrow,
                    ForecastText.time(start, place), chance));
        } else {
            best.setText(getString(R.string.advice_best_at, ForecastText.dayTime(
                    getResources(), start, advice.getComputedAtMillis(), place), chance));
        }
        int noteText = 0;
        if (advice.isNoDaylightCandidate()) {
            noteText = R.string.advice_note_no_daylight;
        } else if (advice.isAllNotAdvisable()) {
            noteText = R.string.advice_note_all_poor;
        } else if (advice.isNoMeaningfulDifference()) {
            noteText = advice.isBestClean() && advice.isSevereChecked()
                    ? R.string.advice_note_no_difference
                    : R.string.advice_note_no_difference_plain;
        }
        String caution = chosen.getSevere() != null
                && chosen.getSevere().getLevel() == SevereWeather.Level.CAUTION
                ? AdviceText.hazard(getResources(), chosen.getSevere()) : null;
        String main = noteText == 0 ? null : getString(noteText);
        showNote(join(main, caution), unchecked);
    }

    /** Shows the summary note lines that are present, or hides the note. */
    private void showNote(String first, String second) {
        String text = join(first, second);
        note.setVisibility(text == null ? View.GONE : View.VISIBLE);
        note.setText(text);
    }

    private static String join(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + "\n" + second;
    }

    private void showTooLongDialog(long tripMillis) {
        if (dialogShownFor == tripMillis) {
            return;
        }
        dialogShownFor = tripMillis;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.advice_too_long_title)
                .setMessage(getString(R.string.advice_too_long_message,
                        HistoryText.formatDuration(getResources(),
                                DepartureAdvisor.MAX_TRIP_MILLIS),
                        HistoryText.formatDuration(getResources(), tripMillis)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
