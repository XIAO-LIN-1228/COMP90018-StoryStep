package com.comp90018.storystep.platform.haptics;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.Objects;

/** A short confirmation pulse that silently does nothing on devices without vibration. */
public final class AndroidArrivalHaptics implements ArrivalHaptics {

    private static final long PULSE_MILLIS = 35L;
    private final Vibrator vibrator;

    @SuppressWarnings("deprecation")
    public AndroidArrivalHaptics(Context context) {
        Context application = Objects.requireNonNull(context, "context").getApplicationContext();
        vibrator = (Vibrator) application.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void pulse() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    PULSE_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            // Retained for minSdk 26's API boundary even though every currently supported build
            // takes the VibrationEffect path.
            vibrator.vibrate(PULSE_MILLIS);
        }
    }
}
