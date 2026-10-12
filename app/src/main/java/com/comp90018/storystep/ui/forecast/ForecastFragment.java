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
import androidx.recyclerview.widget.RecyclerView;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.ui.ViewModelFactory;

/**
 * The next 24 hours of rain chance, hour by hour, for the area the home weather card describes
 * (decision D-08). Opened from the "Hourly" link beside the card.
 */
public class ForecastFragment extends Fragment {

    public static final String ARG_LATITUDE = "latitude";
    public static final String ARG_LONGITUDE = "longitude";
    public static final String ARG_REFERENCE_AREA = "referenceArea";

    private ForecastViewModel viewModel;
    private final RainRowAdapter adapter = new RainRowAdapter();

    private View loading;
    private View errorGroup;
    private TextView errorText;
    private RecyclerView list;
    private TextView zoneNote;

    /** Arguments for opening this page on a point. */
    public static Bundle arguments(GeoPoint origin, boolean referenceArea) {
        Bundle args = new Bundle();
        args.putDouble(ARG_LATITUDE, origin.getLatitude());
        args.putDouble(ARG_LONGITUDE, origin.getLongitude());
        args.putBoolean(ARG_REFERENCE_AREA, referenceArea);
        return args;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forecast, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.forecast_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        loading = view.findViewById(R.id.forecast_loading);
        errorGroup = view.findViewById(R.id.forecast_error_group);
        errorText = view.findViewById(R.id.forecast_error);
        list = view.findViewById(R.id.forecast_list);
        zoneNote = view.findViewById(R.id.forecast_zone_note);
        list.setAdapter(adapter);

        Bundle args = requireArguments();
        viewModel = new ViewModelProvider(this, ViewModelFactory.from(this))
                .get(ForecastViewModel.class);
        viewModel.init(new GeoPoint(args.getDouble(ARG_LATITUDE), args.getDouble(ARG_LONGITUDE)),
                args.getBoolean(ARG_REFERENCE_AREA));

        TextView area = view.findViewById(R.id.forecast_area);
        area.setText(viewModel.isReferenceArea()
                ? R.string.forecast_area_reference : R.string.forecast_area_live);
        view.findViewById(R.id.forecast_retry).setOnClickListener(v -> viewModel.load());
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(ForecastUiState state) {
        loading.setVisibility(state.getStatus() == ForecastUiState.Status.LOADING
                ? View.VISIBLE : View.GONE);
        boolean error = state.getStatus() == ForecastUiState.Status.ERROR;
        errorGroup.setVisibility(error ? View.VISIBLE : View.GONE);
        if (error) {
            errorText.setText(ForecastText.error(state.getReason()));
        }
        list.setVisibility(state.getStatus() == ForecastUiState.Status.CONTENT
                ? View.VISIBLE : View.GONE);
        adapter.submit(state.getHours(), state.getPlace(), state.getReferenceMillis());
        String note = state.getStatus() == ForecastUiState.Status.CONTENT
                ? ForecastText.zoneNote(getResources(), state.getPlace(),
                        System.currentTimeMillis(), java.util.TimeZone.getDefault())
                : null;
        zoneNote.setVisibility(note == null ? View.GONE : View.VISIBLE);
        zoneNote.setText(note);
    }
}
