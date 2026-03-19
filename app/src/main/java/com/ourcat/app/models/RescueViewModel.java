package com.ourcat.app.models;

import android.os.Parcelable;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RescueViewModel extends ViewModel {
    private final MutableLiveData<List<Map<String, Object>>> items = new MutableLiveData<>(new ArrayList<>());
    private int scrollPosition = 0;
    private Parcelable layoutState;

    public MutableLiveData<List<Map<String, Object>>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> newItems) {
        items.setValue(newItems);
    }

    public int getScrollPosition() {
        return scrollPosition;
    }

    public void setScrollPosition(int scrollPosition) {
        this.scrollPosition = scrollPosition;
    }

    public Parcelable getLayoutState() {
        return layoutState;
    }

    public void setLayoutState(Parcelable layoutState) {
        this.layoutState = layoutState;
    }
}
