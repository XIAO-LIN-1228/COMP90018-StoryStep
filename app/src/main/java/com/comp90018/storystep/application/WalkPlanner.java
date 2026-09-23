package com.comp90018.storystep.application;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.plan.WalkPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * The plan being built, and the plan being walked.
 *
 * <p>Process-scoped for the same reason the session coordinator is: the plan is made on one
 * screen, walked on another, and must survive navigation between them. It is deliberately not
 * persisted - a plan is what you are about to do, and a plan left over from last week is noise.
 *
 * <p>Observers are notified on the thread that changed the plan, which is the main thread in
 * every current caller.
 */
public final class WalkPlanner {

    public interface Observer {
        void onPlanChanged(WalkPlan plan);
    }

    private final List<Observer> observers = new ArrayList<>(2);
    private WalkPlan plan = WalkPlan.EMPTY;

    public WalkPlan plan() {
        return plan;
    }

    public void setPlan(WalkPlan next) {
        if (next == null || next.equals(plan)) {
            return;
        }
        plan = next;
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onPlanChanged(plan);
        }
    }

    public void add(Attraction attraction) {
        setPlan(plan.with(attraction));
    }

    public void remove(String attractionId) {
        setPlan(plan.without(attractionId));
    }

    public void move(String attractionId, int by) {
        setPlan(plan.moved(attractionId, by));
    }

    public void clear() {
        setPlan(WalkPlan.EMPTY);
    }

    public void addObserver(Observer observer) {
        observers.add(observer);
        observer.onPlanChanged(plan);
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }
}
