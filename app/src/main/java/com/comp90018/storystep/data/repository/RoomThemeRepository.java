package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.theme.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Theme state belongs to sessions; cache eviction can never remove it. */
public final class RoomThemeRepository implements ThemeRepository {
    private final Transactions transactions;
    private final ThemeDao themes;
    private final SessionDao sessions;
    private final VisitDao visits;
    private final Executor io, main;
    public RoomThemeRepository(Transactions transactions, ThemeDao themes, SessionDao sessions,
            VisitDao visits, Executor io, Executor main) {
        this.transactions = transactions; this.themes = themes; this.sessions = sessions;
        this.visits = visits; this.io = io; this.main = main;
    }
    @Override public void load(String id, RepositoryCallback<ThemeProgress> callback) {
        execute(callback, () -> read(id));
    }
    @Override public void setSkipped(String id, String stopId, boolean skipped, RepositoryCallback<ThemeProgress> callback) {
        execute(callback, () -> {
            ThemeProgress progress = editable(id, stopId);
            if (progress.status(stopId) == ThemeProgress.Status.ARRIVED) {
                throw new IllegalStateException("an arrived stop cannot be skipped");
            }
            if (themes.setSkipped(id, stopId, skipped) != 1) { throw new IllegalStateException("missing stop"); }
            return read(id);
        });
    }
    @Override public void answer(String id, String stopId, int option, RepositoryCallback<ThemeProgress> callback) {
        execute(callback, () -> {
            ThemeProgress progress = editable(id, stopId);
            ThemeStop stop = progress.theme.stop(stopId);
            if (progress.status(stopId) != ThemeProgress.Status.ARRIVED
                    || option < 0 || option >= stop.options.size()) {
                throw new IllegalStateException("question requires arrival and a valid option");
            }
            if (themes.answer(id, stopId, option) != 1) { throw new IllegalStateException("missing stop"); }
            return read(id);
        });
    }
    private ThemeProgress editable(String id, String stopId) {
        ThemeProgress progress = read(id);
        if (progress == null || progress.finished) { throw new IllegalStateException("journey is not editable"); }
        progress.theme.stop(stopId);
        return progress;
    }
    private ThemeProgress read(String id) {
        ThemeRunEntity run = themes.findRun(id);
        SessionEntity session = sessions.findById(id);
        if (run == null || session == null) { return null; }
        Set<String> arrived = new HashSet<>(), skipped = new HashSet<>();
        Map<String, Integer> answers = new HashMap<>();
        for (VisitEntity visit : visits.findBySession(id)) { arrived.add(visit.attractionId); }
        for (ThemeStopEntity stop : themes.findStops(id)) {
            if (stop.skipped) { skipped.add(stop.stopId); }
            if (stop.answer != null) { answers.put(stop.stopId, stop.answer); }
        }
        return new ThemeProgress(id, ThemeJson.decode(run.definitionJson), session.finished, arrived, skipped, answers);
    }
    private interface Work { ThemeProgress run(); }
    private void execute(RepositoryCallback<ThemeProgress> callback, Work work) {
        io.execute(() -> {
            try {
                ThemeProgress[] result = new ThemeProgress[1];
                transactions.runInTransaction(() -> result[0] = work.run());
                main.execute(() -> callback.onSuccess(result[0]));
            } catch (RuntimeException error) { main.execute(() -> callback.onError(error)); }
        });
    }
}
