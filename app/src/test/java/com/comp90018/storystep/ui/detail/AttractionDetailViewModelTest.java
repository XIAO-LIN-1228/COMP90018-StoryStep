package com.comp90018.storystep.ui.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Detail screen state, exercised straight through the repository interface. */
public class AttractionDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static AttractionDetailUiState observe(AttractionDetailViewModel viewModel) {
        final List<AttractionDetailUiState> seen = new ArrayList<>();
        viewModel.getUiState().observeForever(seen::add);
        return seen.isEmpty() ? null : seen.get(seen.size() - 1);
    }

    @Test
    public void loadsAKnownAttraction() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository());

        viewModel.load("melbourne-museum");
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.CONTENT, state.getStatus());
        assertNotNull(state.getAttraction());
        assertEquals("Melbourne Museum", state.getAttraction().getName());
        assertEquals(true, state.getAttraction().isIndoor());
    }

    @Test
    public void anUnknownIdentifierProducesTheErrorState() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository());

        viewModel.load("does-not-exist");
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.ERROR, state.getStatus());
        assertNull(state.getAttraction());
    }

    @Test
    public void aMissingArgumentProducesTheErrorStateInsteadOfCrashing() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository());

        viewModel.load(null);

        assertEquals(AttractionDetailUiState.Status.ERROR, observe(viewModel).getStatus());
    }
}
