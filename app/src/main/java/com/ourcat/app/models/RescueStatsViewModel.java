package com.ourcat.app.models;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RescueStatsViewModel extends ViewModel {
    private final MutableLiveData<Map<String, Object>> stats = new MutableLiveData<>(new HashMap<>());
    private int scrollY = 0;

    public MutableLiveData<Map<String, Object>> getStats() {
        return stats;
    }

    public int getScrollY() {
        return scrollY;
    }

    public void setScrollY(int scrollY) {
        this.scrollY = scrollY;
    }
}
