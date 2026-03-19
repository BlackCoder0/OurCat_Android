package com.ourcat.app.models;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RescueGuideViewModel extends ViewModel {
    private final MutableLiveData<List<Map<String, Object>>> guides = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Map<String, Object>>> contacts = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Map<String, Object>>> articles = new MutableLiveData<>(new ArrayList<>());
    private int scrollY = 0;

    public MutableLiveData<List<Map<String, Object>>> getGuides() {
        return guides;
    }

    public MutableLiveData<List<Map<String, Object>>> getContacts() {
        return contacts;
    }

    public MutableLiveData<List<Map<String, Object>>> getArticles() {
        return articles;
    }

    public int getScrollY() {
        return scrollY;
    }

    public void setScrollY(int scrollY) {
        this.scrollY = scrollY;
    }
}
