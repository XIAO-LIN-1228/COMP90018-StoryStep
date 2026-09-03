package com.comp90018.storystep.platform.sensor;

import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * Local magnetic declination: how far magnetic north sits from true north at a place and time.
 *
 * <p>It is a function of position, so a heading cannot be converted to true north until the session
 * has a fix. That dependency is the whole reason this is an interface rather than a constant: a
 * test supplies a known declination and asserts the conversion, and the app supplies the real one.
 */
public interface DeclinationSource {

    /**
     * @param at               where the reading was taken
     * @param wallClockMillis  when, in epoch milliseconds; the field drifts over years
     * @return degrees to add to a magnetic heading to obtain a true heading; positive east
     */
    float declinationDegrees(GeoPoint at, long wallClockMillis);
}
