package com.ourcat.app.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Wraps a MapView inside a ScrollView.
 * When a finger touches down inside this view, it tells all ancestor
 * scrollable containers to stop intercepting touch events, so the
 * map can be dragged freely. When the finger lifts, interception
 * is restored so the page scrolls normally again.
 */
public class MapTouchWrapper extends FrameLayout {

    public MapTouchWrapper(Context context) {
        super(context);
    }

    public MapTouchWrapper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MapTouchWrapper(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.dispatchTouchEvent(ev);
    }
}
