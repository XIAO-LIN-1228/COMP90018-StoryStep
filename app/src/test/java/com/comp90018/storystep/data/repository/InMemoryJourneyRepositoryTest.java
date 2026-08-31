package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.comp90018.storystep.domain.model.Journey;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import org.junit.Test;

import java.util.List;

public class InMemoryJourneyRepositoryTest {

    @Test
    public void loadJourneys_returnsDeterministicPrototypeHistory() {
        InMemoryJourneyRepository repository = new InMemoryJourneyRepository();
        Holder<List<Journey>> result = new Holder<>();
        Holder<Throwable> error = new Holder<>();

        repository.loadJourneys(callback(result, error));

        assertNull(error.value);
        assertEquals(3, result.value.size());
        assertEquals("heritage-walk", result.value.get(0).getId());
    }

    private static <T> RepositoryCallback<T> callback(Holder<T> result,
                                                       Holder<Throwable> error) {
        return new RepositoryCallback<T>() {
            @Override public void onSuccess(T value) { result.value = value; }
            @Override public void onError(Throwable throwable) { error.value = throwable; }
        };
    }

    private static final class Holder<T> { private T value; }
}
