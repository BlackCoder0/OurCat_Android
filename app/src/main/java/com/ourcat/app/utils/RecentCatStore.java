package com.ourcat.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

public final class RecentCatStore {

    private static final String PREFS_NAME = "ourcat_recent_cat";
    private static final String KEY_ENTRY = "entry";
    private static final Gson GSON = new Gson();

    private RecentCatStore() {
    }

    public static void save(@NonNull Context context, @NonNull Entry entry) {
        prefs(context).edit().putString(KEY_ENTRY, GSON.toJson(entry)).apply();
    }

    @Nullable
    public static Entry read(@NonNull Context context) {
        String raw = prefs(context).getString(KEY_ENTRY, null);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            Entry entry = GSON.fromJson(raw, Entry.class);
            return entry != null && entry.isValid() ? entry : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().remove(KEY_ENTRY).apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static final class Entry {
        public long catId;
        public String name = "";
        public String status = "";
        public String feature = "";
        public String personality = "";
        public String imageUrl = "";
        public int reportCount;
        public boolean hasActiveRescue;
        public long rescueSquarePostId;
        public long viewedAt;

        public boolean isValid() {
            return catId > 0;
        }
    }
}
