package com.comp90018.storystep.domain.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class WalkPlanTest {

    private static Attraction place(String id, double lat, double lon) {
        return Attraction.seeded(id, id, AttractionCategory.CULTURAL_SITE, "",
                IndoorClassification.UNKNOWN, new GeoPoint(lat, lon));
    }

    private static final GeoPoint ORIGIN = new GeoPoint(-37.7963, 144.9614);

    @Test
    public void addingKeepsOrderAndRefusesDuplicatesAndOverflow() {
        WalkPlan plan = WalkPlan.EMPTY
                .with(place("a", -37.7981, 144.9591))
                .with(place("b", -37.7975, 144.9640));
        assertEquals(Arrays.asList("a", "b"), ids(plan));
        assertSame("a duplicate changes nothing", plan, plan.with(place("a", -37.79, 144.96)));
        for (int i = 0; i < WalkPlan.MAX_STOPS; i++) {
            plan = plan.with(place("x" + i, -37.79 - i * 0.001, 144.96));
        }
        assertEquals(WalkPlan.MAX_STOPS, plan.size());
        assertTrue(plan.isFull());
        assertSame("a full plan takes no more", plan, plan.with(place("last", -37.7, 144.9)));
    }

    @Test
    public void removingAndMovingAreExactAndClampAtTheEnds() {
        WalkPlan plan = WalkPlan.of(Arrays.asList(
                place("a", -37.798, 144.959), place("b", -37.797, 144.964),
                place("c", -37.796, 144.969)));
        assertEquals(Arrays.asList("a", "c"), ids(plan.without("b")));
        assertSame(plan, plan.without("nobody"));
        assertEquals(Arrays.asList("b", "a", "c"), ids(plan.moved("b", -1)));
        assertEquals(Arrays.asList("a", "c", "b"), ids(plan.moved("b", 1)));
        assertSame("the first cannot move up", plan, plan.moved("a", -1));
        assertSame("the last cannot move down", plan, plan.moved("c", 1));
    }

    @Test
    public void nearestNeighbourOrdersFromWhereTheWalkerIs() {
        WalkPlan plan = WalkPlan.of(Arrays.asList(
                place("a", -37.8100, 144.9614),
                place("b", -37.8020, 144.9614),
                place("c", -37.7970, 144.9614)));
        assertEquals(Arrays.asList("c", "b", "a"), ids(plan.orderedFrom(ORIGIN)));
        WalkPlan pair = WalkPlan.of(Arrays.asList(
                place("far", -37.81, 144.9614), place("near", -37.797, 144.9614)));
        assertSame("two stops are already in the order the walker chose",
                pair, pair.orderedFrom(ORIGIN));
    }

    @Test
    public void distanceIsMeasuredFromTheOriginThroughEveryStop() {
        WalkPlan plan = WalkPlan.of(Arrays.asList(
                place("a", -37.8000, 144.9614), place("b", -37.8100, 144.9614)));
        double metres = plan.distanceMeters(ORIGIN);
        assertTrue("expected roughly 1.5 km, got " + metres, metres > 1400 && metres < 1600);
        assertEquals(0.0, WalkPlan.EMPTY.distanceMeters(ORIGIN), 0.001);
    }

    @Test
    public void durationIsTheWalkingPlusAStayAtEachStop() {
        WalkPlan plan = WalkPlan.of(Arrays.asList(
                place("a", -37.8000, 144.9614), place("b", -37.8100, 144.9614)));
        long walking = Math.round(plan.distanceMeters(ORIGIN) / WalkPlan.WALKING_SPEED_MPS * 1000);
        assertEquals(walking + 2 * WalkPlan.DWELL_MILLIS_PER_STOP, plan.durationMillis(ORIGIN));
        assertEquals(0, WalkPlan.EMPTY.durationMillis(ORIGIN));
    }

    @Test
    public void theWalkKnowsWhereItStartsAndWhatComesNext() {
        WalkPlan plan = WalkPlan.of(Arrays.asList(
                place("a", -37.798, 144.959), place("b", -37.797, 144.964)));
        assertEquals("a", plan.first().getId());
        assertEquals("b", plan.after("a").getId());
        assertNull("nothing comes after the last stop", plan.after("b"));
        assertNull(plan.after("nobody"));
        assertNull(WalkPlan.EMPTY.first());
        assertTrue(WalkPlan.EMPTY.isEmpty());
        assertFalse(plan.isEmpty());
    }

    @Test
    public void thePathRunsFromTheOriginThroughTheStops() {
        WalkPlan plan = WalkPlan.of(Arrays.asList(
                place("a", -37.798, 144.959), place("b", -37.797, 144.964)));
        assertEquals(3, plan.path(ORIGIN).size());
        assertEquals(2, plan.path(null).size());
    }

    private static List<String> ids(WalkPlan plan) {
        List<String> ids = new java.util.ArrayList<>();
        for (Attraction stop : plan.getStops()) {
            ids.add(stop.getId());
        }
        return ids;
    }
}
