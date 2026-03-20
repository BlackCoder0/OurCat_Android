package com.ourcat.app.utils;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

public final class PressFeedbackUtils {

    private static final float DEFAULT_PRESSED_SCALE = 0.98f;
    private static final long PRESS_IN_DURATION_MS = 100L;
    private static final long PRESS_OUT_DURATION_MS = 160L;

    private PressFeedbackUtils() {
    }

    public static void attach(@NonNull View view) {
        attach(view, DEFAULT_PRESSED_SCALE);
    }

    public static void attach(@NonNull View view, float pressedScale) {
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate()
                        .scaleX(pressedScale)
                        .scaleY(pressedScale)
                        .setDuration(PRESS_IN_DURATION_MS)
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(PRESS_OUT_DURATION_MS)
                        .start();
            }
            return false;
        });
    }
}
