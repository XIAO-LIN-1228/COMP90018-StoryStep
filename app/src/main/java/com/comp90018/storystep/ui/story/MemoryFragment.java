package com.comp90018.storystep.ui.story;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.comp90018.storystep.R;

/** Local-first memory editor. A Photo Picker and persistence use case are intentionally deferred. */
public class MemoryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.memory_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.add_photo_card).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.memory_photo_picker_demo,
                        Toast.LENGTH_SHORT).show());
        View.OnClickListener finish = v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_memory_to_summary);
        view.findViewById(R.id.save_memory_button).setOnClickListener(finish);
        view.findViewById(R.id.skip_memory_button).setOnClickListener(finish);
    }
}
