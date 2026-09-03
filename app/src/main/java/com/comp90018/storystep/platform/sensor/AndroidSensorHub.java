package com.comp90018.storystep.platform.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;

import com.comp90018.storystep.domain.model.MotionSample;

/**
 * The app's only {@link SensorManager} registration, serving both motion and heading.
 *
 * <p>Both features need the accelerometer. Registering it twice would double the callback rate and
 * the power cost for readings that are identical, so this class registers it <b>once</b> and fans
 * the samples out: raw to the motion listener, and combined with the magnetic field to produce a
 * heading. The magnetometer is registered only while a heading is actually wanted.
 *
 * <p>Two details of the sensor API shape this code:
 *
 * <ul>
 *   <li>{@code SensorEvent.values} is a reused array. Its contents are overwritten before the next
 *       callback, so anything kept beyond the callback must be copied — otherwise the classifier
 *       reads whatever the sensor produced most recently instead of the sample it was given.
 *   <li>The requested rate is a hint. The platform may deliver faster or slower, so every interval
 *       downstream is derived from {@code SensorEvent.timestamp} and never from an assumed cadence.
 * </ul>
 *
 * <p>Callbacks arrive on the main thread, matching every other source in the app.
 */
public final class AndroidSensorHub implements MotionSource, SensorEventListener {

    /**
     * About 20 Hz. Fast enough to see a walking cadence, slow enough not to flood the main thread;
     * a starting value to tune against recorded trials, not a guarantee.
     */
    private static final int SAMPLING_PERIOD_MICROS = 50_000;

    /** Smoothing applied to the gravity estimate used for the rotation matrix, per reading. */
    private static final float GRAVITY_ALPHA = 0.2f;

    private final SensorManager sensorManager;
    private final DisplayManager displayManager;
    private final Sensor accelerometer;
    private final Sensor magnetometer;

    private final float[] accelerometerValues = new float[3];
    private final float[] magnetometerValues = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private final float[] orientation = new float[3];

    private MotionSource.Listener motionListener;
    private HeadingSource.Listener headingListener;

    private boolean registeredForAccelerometer;
    private boolean registeredForMagnetometer;
    private boolean hasAccelerometerValues;
    private boolean hasMagnetometerValues;

    /**
     * Defaults to {@code MEDIUM} rather than {@code UNRELIABLE}: the platform reports accuracy only
     * when it changes, so never having heard is not evidence of a bad sensor. {@code UNRELIABLE} is
     * reserved for an accuracy the device actually reported.
     */
    private HeadingAccuracy headingAccuracy = HeadingAccuracy.MEDIUM;

    public AndroidSensorHub(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.sensorManager =
                (SensorManager) applicationContext.getSystemService(Context.SENSOR_SERVICE);
        this.displayManager =
                (DisplayManager) applicationContext.getSystemService(Context.DISPLAY_SERVICE);
        this.accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.magnetometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    // --- MotionSource ---------------------------------------------------------------------------

    @Override
    public boolean isAvailable() {
        return accelerometer != null;
    }

    @Override
    public void startUpdates(MotionSource.Listener listener) {
        motionListener = listener;
        syncRegistrations();
    }

    @Override
    public void stopUpdates() {
        motionListener = null;
        syncRegistrations();
    }

    // --- HeadingSource --------------------------------------------------------------------------

    /** A heading needs both sensors; either one missing means no compass at all. */
    public boolean isHeadingAvailable() {
        return accelerometer != null && magnetometer != null;
    }

    public void startHeadingUpdates(HeadingSource.Listener listener) {
        headingListener = listener;
        syncRegistrations();
    }

    public void stopHeadingUpdates() {
        headingListener = null;
        hasMagnetometerValues = false;
        syncRegistrations();
    }

    /**
     * Adapts this hub to {@link HeadingSource}.
     *
     * <p>A separate view rather than implementing both interfaces' identically named methods on one
     * object: {@code isAvailable} and {@code startUpdates} mean different things for motion and for
     * heading, and collapsing them would make "available" ambiguous on a device that has an
     * accelerometer but no magnetometer.
     */
    public HeadingSource asHeadingSource() {
        return new HeadingSource() {
            @Override
            public boolean isAvailable() {
                return isHeadingAvailable();
            }

            @Override
            public void startUpdates(Listener listener) {
                startHeadingUpdates(listener);
            }

            @Override
            public void stopUpdates() {
                stopHeadingUpdates();
            }
        };
    }

    // --- registration ---------------------------------------------------------------------------

    /**
     * Brings the actual registrations in line with what is currently wanted.
     *
     * <p>Expressed as "what should be registered now" rather than as register/unregister calls at
     * each call site, because the accelerometer has two independent claimants and reference
     * counting them by hand is exactly how a sensor gets left running after a session ends.
     */
    private void syncRegistrations() {
        if (sensorManager == null) {
            return;
        }
        boolean wantAccelerometer =
                accelerometer != null && (motionListener != null || headingListener != null);
        boolean wantMagnetometer = magnetometer != null && headingListener != null;

        if (wantAccelerometer && !registeredForAccelerometer) {
            sensorManager.registerListener(this, accelerometer, SAMPLING_PERIOD_MICROS);
            registeredForAccelerometer = true;
        } else if (!wantAccelerometer && registeredForAccelerometer) {
            sensorManager.unregisterListener(this, accelerometer);
            registeredForAccelerometer = false;
            hasAccelerometerValues = false;
        }

        if (wantMagnetometer && !registeredForMagnetometer) {
            sensorManager.registerListener(this, magnetometer, SAMPLING_PERIOD_MICROS);
            registeredForMagnetometer = true;
        } else if (!wantMagnetometer && registeredForMagnetometer) {
            sensorManager.unregisterListener(this, magnetometer);
            registeredForMagnetometer = false;
            hasMagnetometerValues = false;
        }
    }

    // --- SensorEventListener --------------------------------------------------------------------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            onAccelerometerEvent(event);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            // Copied out of the reused array before it is overwritten.
            System.arraycopy(event.values, 0, magnetometerValues, 0, 3);
            hasMagnetometerValues = true;
            publishHeading();
        }
    }

    private void onAccelerometerEvent(SensorEvent event) {
        if (motionListener != null) {
            motionListener.onMotionSample(new MotionSample(
                    event.values[0], event.values[1], event.values[2],
                    toMonotonicMillis(event.timestamp)));
        }
        if (headingListener == null) {
            return;
        }
        // The rotation matrix wants gravity, not the raw reading: a hand swinging while walking
        // would otherwise spin the compass with every step. Low-pass filtering isolates the
        // constant downward component the matrix actually needs.
        for (int axis = 0; axis < 3; axis++) {
            accelerometerValues[axis] = hasAccelerometerValues
                    ? accelerometerValues[axis]
                            + GRAVITY_ALPHA * (event.values[axis] - accelerometerValues[axis])
                    : event.values[axis];
        }
        hasAccelerometerValues = true;
        publishHeading();
    }

    private void publishHeading() {
        if (headingListener == null || !hasAccelerometerValues || !hasMagnetometerValues) {
            return;
        }
        if (!SensorManager.getRotationMatrix(
                rotationMatrix, null, accelerometerValues, magnetometerValues)) {
            // No solution: free fall, or a magnetic field the device cannot make sense of. Saying
            // nothing is right — repeating the previous heading would look like a working compass.
            return;
        }
        float[] matrix = remapForDisplayRotation();
        SensorManager.getOrientation(matrix, orientation);
        float degrees = (float) Math.toDegrees(orientation[0]);
        float normalized = (degrees % 360.0f + 360.0f) % 360.0f;
        headingListener.onHeadingChanged(normalized == 360.0f ? 0.0f : normalized, headingAccuracy);
    }

    /**
     * Rotates the device axes onto the axes the user is actually looking at.
     *
     * <p>Without this the compass is correct only in portrait: the sensor frame is fixed to the
     * hardware, so turning the phone sideways turns the reported heading by the same 90 degrees.
     */
    private float[] remapForDisplayRotation() {
        switch (displayRotation()) {
            case Surface.ROTATION_90:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix);
                return remappedMatrix;
            case Surface.ROTATION_180:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix);
                return remappedMatrix;
            case Surface.ROTATION_270:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix);
                return remappedMatrix;
            default:
                return rotationMatrix;
        }
    }

    /**
     * The default display's rotation.
     *
     * <p>Read through {@link DisplayManager} rather than {@code WindowManager.getDefaultDisplay()},
     * which is deprecated, or {@code Context.getDisplay()}, which refuses to answer for the
     * application context this hub is built with.
     */
    private int displayRotation() {
        if (displayManager == null) {
            return Surface.ROTATION_0;
        }
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        return display == null ? Surface.ROTATION_0 : display.getRotation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) {
            return;
        }
        headingAccuracy = toHeadingAccuracy(accuracy);
    }

    private static HeadingAccuracy toHeadingAccuracy(int sensorStatus) {
        switch (sensorStatus) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                return HeadingAccuracy.HIGH;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                return HeadingAccuracy.MEDIUM;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                return HeadingAccuracy.LOW;
            default:
                // Covers SENSOR_STATUS_UNRELIABLE and SENSOR_STATUS_NO_CONTACT alike: both mean
                // the reading should not be pointed at anything.
                return HeadingAccuracy.UNRELIABLE;
        }
    }

    /**
     * Converts a sensor event timestamp to the same boot-relative milliseconds the rest of the app
     * uses. Sensor timestamps are nanoseconds since boot, the same origin as
     * {@link SystemClock#elapsedRealtimeNanos()}, so this is a unit change and not a re-basing.
     */
    private static long toMonotonicMillis(long eventTimestampNanos) {
        return eventTimestampNanos / 1_000_000L;
    }
}
